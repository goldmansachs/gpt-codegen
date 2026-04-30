package org.rj.modelgen.ui.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Parses and serializes the {@code $defs} section of a {@code common_types.json} file
 * into a structured, human-readable text block for inclusion in LLM prompts.
 *
 * <p>Each type definition is emitted with its full structure — properties, types,
 * descriptions, enum constraints, defaults, required status, and nested objects —
 * so the LLM has complete context for producing valid A2UI payloads.</p>
 *
 * <p>The raw JSON from {@code common_types.json} (or an overridden variant such as
 * {@code extended_common_types.json}) remains the single source of truth; this
 * serializer dynamically derives the prompt text from that JSON.</p>
 */
public class A2UICommonTypeSerializer {

    private A2UICommonTypeSerializer() {}

    /**
     * Parses the given common types JSON string and serializes every {@code $defs}
     * entry into a human-readable text block.
     *
     * @param commonTypesJson the raw JSON content of a common_types file
     * @return the serialized text, or an empty string if the JSON is blank or unparseable
     */
    public static String serialize(String commonTypesJson) {
        if (commonTypesJson == null || commonTypesJson.isBlank()) return "";

        try {
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(commonTypesJson);
            final JsonNode defs = root.path("$defs");
            if (defs.isMissingNode() || !defs.isObject()) return "";

            final StringBuilder sb = new StringBuilder();
            final Iterator<Map.Entry<String, JsonNode>> fields = defs.fields();
            boolean first = true;

            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> entry = fields.next();
                if (!first) sb.append("\n\n");
                first = false;
                serializeTypeDef(sb, entry.getKey(), entry.getValue(), 0);
            }

            return sb.toString();
        } catch (Exception e) {
            return "(failed to parse common types: " + e.getMessage() + ")";
        }
    }

    // -----------------------------------------------------------------------
    //  Top-level type definition
    // -----------------------------------------------------------------------

    private static void serializeTypeDef(StringBuilder sb, String name, JsonNode schema, int indent) {
        final String pad = "  ".repeat(indent);

        // Header line: **TypeName** — description
        sb.append(pad).append("**").append(name).append("**");
        appendDescription(sb, schema);
        sb.append("\n");

        // Determine the shape of this type.
        // If the schema is an object with properties, prefer that over a top-level oneOf
        // (which is typically a validation constraint, e.g. FunctionCall's oneOf for anyFunction).
        if (isObjectType(schema) && schema.has("properties")) {
            serializeObjectProperties(sb, schema, indent);
        } else if (schema.has("oneOf")) {
            serializeOneOf(sb, schema, indent);
        } else if (schema.has("enum")) {
            serializeEnum(sb, schema, indent);
        } else if (isObjectType(schema)) {
            // Object without properties (rare)
            sb.append(pad).append("  Type: object\n");
        } else if (schema.has("type")) {
            sb.append(pad).append("  Type: ").append(schema.get("type").asText()).append("\n");
        }
    }

    // -----------------------------------------------------------------------
    //  Enum types  (e.g. CheckOperator)
    // -----------------------------------------------------------------------

    private static void serializeEnum(StringBuilder sb, JsonNode schema, int indent) {
        final String pad = "  ".repeat(indent);
        final String type = schema.has("type") ? schema.get("type").asText() : "string";
        sb.append(pad).append("  Type: ").append(type).append("\n");
        sb.append(pad).append("  Allowed values: ");
        sb.append(joinEnumValues(schema.get("enum")));
        sb.append("\n");
    }

    // -----------------------------------------------------------------------
    //  Union types  (e.g. DynamicString, ChildList, Action)
    // -----------------------------------------------------------------------

    private static void serializeOneOf(StringBuilder sb, JsonNode schema, int indent) {
        final String pad = "  ".repeat(indent);
        sb.append(pad).append("  One of:\n");

        int variantIndex = 1;
        for (JsonNode variant : schema.get("oneOf")) {
            sb.append(pad).append("  ").append(variantIndex++).append(". ");
            serializeVariant(sb, variant, indent + 2);
        }

        // discriminator hint
        if (schema.has("discriminator")) {
            final String propName = schema.path("discriminator").path("propertyName").asText("");
            if (!propName.isEmpty()) {
                sb.append(pad).append("  Discriminator: \"").append(propName).append("\"\n");
            }
        }
    }

    private static void serializeVariant(StringBuilder sb, JsonNode variant, int indent) {
        if (variant.has("$ref")) {
            sb.append(resolveRefName(variant.get("$ref").asText()));
            sb.append("\n");
            return;
        }

        if (variant.has("allOf")) {
            // Pattern: allOf [ {$ref: FunctionCall}, {properties: {returnType: {const: "X"}}} ]
            final List<String> parts = new ArrayList<>();
            for (JsonNode entry : variant.get("allOf")) {
                if (entry.has("$ref")) {
                    parts.add(resolveRefName(entry.get("$ref").asText()));
                } else if (entry.has("properties")) {
                    final JsonNode constNode = entry.path("properties").path("returnType").path("const");
                    if (!constNode.isMissingNode()) {
                        parts.add("returnType=\"" + constNode.asText() + "\"");
                    }
                }
            }
            sb.append(String.join(" with ", parts)).append("\n");
            return;
        }

        if (variant.has("type")) {
            final String type = variant.get("type").asText();
            sb.append(type);
            // array with items
            if ("array".equals(type) && variant.has("items")) {
                sb.append("<").append(resolveTypeLabel(variant.get("items"))).append(">");
            }
            appendDescription(sb, variant);

            // If this is an inline object with properties, expand them
            if ("object".equals(type) && variant.has("properties")) {
                sb.append("\n");
                serializeObjectProperties(sb, variant, indent);
                return;
            }
            sb.append("\n");
            return;
        }

        sb.append("(unknown variant)\n");
    }

    // -----------------------------------------------------------------------
    //  Object types  (e.g. DataBinding, RuleCondition, ComponentCommon, Checkable)
    // -----------------------------------------------------------------------

    static void serializeObjectProperties(StringBuilder sb, JsonNode schema, int indent) {
        final JsonNode props = schema.path("properties");
        if (!props.isObject() || props.isEmpty()) return;

        final Set<String> requiredSet = new LinkedHashSet<>();
        final JsonNode reqNode = schema.path("required");
        if (reqNode.isArray()) {
            for (JsonNode r : reqNode) {
                requiredSet.add(r.asText());
            }
        }

        final Iterator<Map.Entry<String, JsonNode>> propFields = props.fields();
        while (propFields.hasNext()) {
            final Map.Entry<String, JsonNode> prop = propFields.next();
            serializeProperty(sb, prop.getKey(), prop.getValue(), requiredSet.contains(prop.getKey()), indent + 1);
        }
    }

    static void serializeProperty(StringBuilder sb, String name, JsonNode propSchema,
                                          boolean required, int indent) {
        final String pad = "  ".repeat(indent);
        final String typeLabel = resolveTypeLabel(propSchema);

        sb.append(pad).append("- ").append(name);
        sb.append(" (").append(typeLabel).append(")");

        if (required) {
            sb.append(" [required]");
        }

        // const value
        if (propSchema.has("const")) {
            sb.append(" = \"").append(propSchema.get("const").asText()).append("\"");
        }

        // default value
        if (propSchema.has("default")) {
            sb.append(" [default: ").append(propSchema.get("default").asText()).append("]");
        }

        // enum values (inline)
        if (propSchema.has("enum")) {
            sb.append(" — values: ").append(joinEnumValues(propSchema.get("enum")));
        }

        // description
        appendDescription(sb, propSchema);

        sb.append("\n");

        // Recurse into nested object properties (e.g. visibility, componentRequired, disabled, event)
        if (isObjectType(propSchema) && propSchema.has("properties")) {
            serializeObjectProperties(sb, propSchema, indent);
        }

        // Recurse into nested oneOf (e.g. Action's oneOf inside a property)
        if (propSchema.has("oneOf") && !propSchema.has("type")) {
            serializeOneOf(sb, propSchema, indent);
        }

        // Array items detail — if items reference a $ref or have structure
        if ("array".equals(propSchema.path("type").asText("")) && propSchema.has("items")) {
            final JsonNode items = propSchema.get("items");
            if (items.has("properties")) {
                sb.append(pad).append("  Each item:\n");
                serializeObjectProperties(sb, items, indent + 1);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    static boolean isObjectType(JsonNode schema) {
        return "object".equals(schema.path("type").asText(""));
    }

    /**
     * Resolves a human-readable type label for a property schema node.
     */
    private static String resolveTypeLabel(JsonNode propSchema) {
        // $ref
        if (propSchema.has("$ref")) {
            return resolveRefName(propSchema.get("$ref").asText());
        }

        // allOf with $ref (e.g. DynamicString wrapped in allOf)
        if (propSchema.has("allOf") && propSchema.get("allOf").isArray()) {
            for (JsonNode entry : propSchema.get("allOf")) {
                if (entry.has("$ref")) {
                    return resolveRefName(entry.get("$ref").asText());
                }
            }
        }

        // oneOf
        if (propSchema.has("oneOf")) {
            final List<String> types = new ArrayList<>();
            for (JsonNode option : propSchema.get("oneOf")) {
                types.add(resolveTypeLabel(option));
            }
            return String.join(" | ", types);
        }

        // anyOf (used in FunctionCall args additionalProperties)
        if (propSchema.has("anyOf")) {
            final List<String> types = new ArrayList<>();
            for (JsonNode option : propSchema.get("anyOf")) {
                types.add(resolveTypeLabel(option));
            }
            return String.join(" | ", types);
        }

        // const
        if (propSchema.has("const")) {
            return "const";
        }

        // Inline type
        if (propSchema.has("type")) {
            final String t = propSchema.get("type").asText();
            if ("array".equals(t) && propSchema.has("items")) {
                return "array<" + resolveTypeLabel(propSchema.get("items")) + ">";
            }
            if ("object".equals(t) && propSchema.has("additionalProperties")) {
                return "object<" + resolveTypeLabel(propSchema.get("additionalProperties")) + ">";
            }
            return t;
        }

        // Bare schema with just description (e.g. FunctionCall arg "value" with no type)
        if (propSchema.has("description") && propSchema.size() == 1) {
            return "any";
        }

        return "any";
    }

    /**
     * Extracts the type name from a {@code $ref} string.
     * e.g. {@code "#/$defs/DynamicString"} → {@code "DynamicString"},
     *      {@code "catalog.json#/$defs/anyFunction"} → {@code "anyFunction"}
     */
    private static String resolveRefName(String ref) {
        final int defsIdx = ref.indexOf("/$defs/");
        if (defsIdx >= 0) {
            return ref.substring(defsIdx + "/$defs/".length());
        }
        // Fallback: return the fragment after #, or the whole ref
        final int hashIdx = ref.indexOf('#');
        if (hashIdx >= 0 && hashIdx < ref.length() - 1) {
            return ref.substring(hashIdx + 1);
        }
        return ref;
    }

    static String joinEnumValues(JsonNode enumNode) {
        if (enumNode == null || !enumNode.isArray()) return "";
        final List<String> values = new ArrayList<>();
        for (JsonNode v : enumNode) {
            values.add("\"" + v.asText() + "\"");
        }
        return String.join(", ", values);
    }

    static void appendDescription(StringBuilder sb, JsonNode schema) {
        if (schema.has("description")) {
            sb.append(" — ").append(schema.get("description").asText());
        }
    }
}

