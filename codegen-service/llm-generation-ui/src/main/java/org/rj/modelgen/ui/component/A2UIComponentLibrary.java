package org.rj.modelgen.ui.component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rj.modelgen.llm.component.ComponentLibrary;
import org.rj.modelgen.llm.util.Util;

import java.util.*;

/**
 * Component library representing the A2UI Protocol v0.9 catalog of available
 * components, common type, functions, and structural rules.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class A2UIComponentLibrary extends ComponentLibrary<A2UIComponent> {

    private static final String DEFAULT_COMMON_TYPES_RESOURCE = "schemas/common_types.json";

    private List<A2UIComponent> components;
    private final List<A2UIFunctionDefinition> functions;
    private final String commonTypesJson;

    protected A2UIComponentLibrary(List<A2UIComponent> components, List<A2UIFunctionDefinition> functions,
                                 String commonTypesJson) {
        super(components);
        this.components = components != null ? new ArrayList<>(components) : new ArrayList<>();
        this.functions = functions != null ? new ArrayList<>(functions) : new ArrayList<>();
        this.commonTypesJson = commonTypesJson != null ? commonTypesJson : "";
    }

    @Override
    public ComponentLibrary<A2UIComponent> constructEmpty() {
        return new A2UIComponentLibrary(List.of(), List.of(), "");
    }

    @Override
    public List<A2UIComponent> getComponents() {
        return components;
    }

    @Override
    public void setComponents(List<A2UIComponent> a2UIComponents) {
        this.components = a2UIComponents != null ? new ArrayList<>(a2UIComponents) : new ArrayList<>();
    }

    public List<A2UIFunctionDefinition> getFunctions() {
        return functions;
    }

    /**
     * Returns the raw common types JSON string (the content of {@code common_types.json}
     * or an override supplied at construction time).
     */
    public String getCommonTypesJson() {
        return commonTypesJson;
    }

    /**
     * Returns the default common types JSON loaded from the classpath
     * ({@value #DEFAULT_COMMON_TYPES_RESOURCE}).
     */
    public static String defaultCommonTypes() {
        return Util.loadStringResource(DEFAULT_COMMON_TYPES_RESOURCE);
    }

    public static A2UIComponentLibrary defaultLibrary() {
        return fromCatalogResource("schemas/basic_catalog.json");
    }

    /**
     * Parses the A2UI catalog JSON Schema into a structured component library,
     * using the default {@code common_types.json} from the classpath.
     */
    public static A2UIComponentLibrary fromCatalogResource(String resource) {
        final String json = Util.loadStringResource(resource);
        return fromCatalogJson(json, defaultCommonTypes());
    }

    /**
     * Parses the A2UI catalog JSON Schema into a structured component library,
     * using a custom common types JSON string.
     */
    public static A2UIComponentLibrary fromCatalogResource(String resource, String commonTypesJson) {
        final String json = Util.loadStringResource(resource);
        return fromCatalogJson(json, commonTypesJson);
    }

    /**
     * Parses raw A2UI catalog JSON Schema string into a structured component library,
     * using the default {@code common_types.json} from the classpath.
     */
    public static A2UIComponentLibrary fromCatalogJson(String json) {
        return fromCatalogJson(json, defaultCommonTypes());
    }

    /**
     * Parses raw A2UI catalog JSON Schema string into a structured component library,
     * with a custom common types JSON string.
     */
    public static A2UIComponentLibrary fromCatalogJson(String json, String commonTypesJson) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(json);

            final List<A2UIFunctionDefinition> functions = parseFunctions(root.path("functions"));

            final A2UIComponentLibrary library = new A2UIComponentLibrary(List.of(), functions, commonTypesJson);
            final List<A2UIComponent> components = library.parseComponentList(root.path("components"));

            return new A2UIComponentLibrary(components, functions, commonTypesJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse A2UI catalog JSON: " + e.getMessage(), e);
        }
    }

    // --- Component parsing ---

    private static final Set<String> CHECKABLE_COMPONENTS = Set.of(
            "Button", "TextField", "CheckBox", "ChoicePicker", "Slider", "DateTimeInput"
    );

    protected List<A2UIComponent> parseComponentList(JsonNode componentsNode) {
        if (componentsNode.isMissingNode() || !componentsNode.isObject()) return List.of();

        final List<A2UIComponent> result = new ArrayList<>();
        final Iterator<Map.Entry<String, JsonNode>> fields = componentsNode.fields();

        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> entry = fields.next();
            final String componentType = entry.getKey();
            final JsonNode componentSchema = entry.getValue();

            result.add(parseComponent(componentType, componentSchema));
        }
        return result;
    }

    protected A2UIComponent parseComponent(String componentType, JsonNode schema) {
        return parseOneComponent(componentType, schema, this);
    }

    private static A2UIComponent parseOneComponent(String componentType, JsonNode schema, A2UIComponentLibrary instance) {
        // Merge all property definitions from allOf entries
        final Map<String, JsonNode> allProperties = new LinkedHashMap<>();
        final Set<String> allRequired = new LinkedHashSet<>();
        String description = null;

        if (schema.has("description")) {
            description = schema.get("description").asText();
        }

        final JsonNode allOf = schema.path("allOf");
        if (allOf.isArray()) {
            for (JsonNode entry : allOf) {
                // Skip $ref-only entries (ComponentCommon, CatalogComponentCommon, Checkable)
                if (entry.has("$ref") && !entry.has("properties")) continue;

                if (entry.has("description") && description == null) {
                    description = entry.get("description").asText();
                }

                final JsonNode props = entry.path("properties");
                if (props.isObject()) {
                    props.fields().forEachRemaining(p -> allProperties.put(p.getKey(), p.getValue()));
                }

                final JsonNode req = entry.path("required");
                if (req.isArray()) {
                    for (JsonNode r : req) {
                        allRequired.add(r.asText());
                    }
                }
            }
        }

        // Remove the 'component' const field — it's the discriminator, not a user-facing field
        allProperties.remove("component");
        allRequired.remove("component");

        // Partition into required vs optional FieldSpecs
        final List<A2UIComponent.FieldSpec> requiredFields = new ArrayList<>();
        final List<A2UIComponent.FieldSpec> optionalFields = new ArrayList<>();

        for (Map.Entry<String, JsonNode> prop : allProperties.entrySet()) {
            final String fieldName = prop.getKey();
            final JsonNode fieldSchema = prop.getValue();
            final A2UIComponent.FieldSpec spec = instance.toFieldSpec(fieldName, fieldSchema);

            if (allRequired.contains(fieldName)) {
                requiredFields.add(spec);
            } else {
                optionalFields.add(spec);
            }
        }

        final boolean checkable = CHECKABLE_COMPONENTS.contains(componentType);

        return new A2UIComponent(componentType, description != null ? description : "", requiredFields, optionalFields, checkable);
    }

    protected A2UIComponent.FieldSpec toFieldSpec(String name, JsonNode fieldSchema) {
        String type = resolveFieldType(fieldSchema);
        String fieldDescription = fieldSchema.has("description") ? fieldSchema.get("description").asText() : "";
        List<String> enumValues = extractEnumValues(fieldSchema);

        return new A2UIComponent.FieldSpec(name, type, fieldDescription, enumValues);
    }

    private static String resolveFieldType(JsonNode fieldSchema) {
        // Check for $ref first (DynamicString, DynamicBoolean, ChildList, ComponentId, Action, etc.)
        if (fieldSchema.has("$ref")) {
            final String ref = fieldSchema.get("$ref").asText();
            final int defsIdx = ref.indexOf("/$defs/");
            if (defsIdx >= 0) {
                return ref.substring(defsIdx + "/$defs/".length());
            }
            return ref;
        }

        // allOf with $ref — e.g. DateTimeInput min/max which wraps DynamicString in allOf
        if (fieldSchema.has("allOf") && fieldSchema.get("allOf").isArray()) {
            for (JsonNode entry : fieldSchema.get("allOf")) {
                if (entry.has("$ref")) {
                    return resolveFieldType(entry);
                }
            }
        }

        // oneOf — e.g. Icon name (string enum | path object)
        if (fieldSchema.has("oneOf")) {
            final List<String> types = new ArrayList<>();
            for (JsonNode option : fieldSchema.get("oneOf")) {
                types.add(resolveFieldType(option));
            }
            return String.join(" | ", types);
        }

        // Inline type
        if (fieldSchema.has("type")) {
            final String t = fieldSchema.get("type").asText();
            if ("array".equals(t) && fieldSchema.has("items")) {
                return "array<" + resolveFieldType(fieldSchema.get("items")) + ">";
            }
            if ("object".equals(t)) {
                return "object";
            }
            return t;
        }

        return "unknown";
    }

    private static List<String> extractEnumValues(JsonNode fieldSchema) {
        if (fieldSchema.has("enum") && fieldSchema.get("enum").isArray()) {
            final List<String> values = new ArrayList<>();
            for (JsonNode v : fieldSchema.get("enum")) {
                values.add(v.asText());
            }
            return values;
        }

        // Check within oneOf for enum entries
        if (fieldSchema.has("oneOf")) {
            for (JsonNode option : fieldSchema.get("oneOf")) {
                final List<String> nested = extractEnumValues(option);
                if (!nested.isEmpty()) return nested;
            }
        }

        return List.of();
    }


    // --- Function parsing ---

    private static List<A2UIFunctionDefinition> parseFunctions(JsonNode functionsNode) {
        if (functionsNode.isMissingNode() || !functionsNode.isObject()) return List.of();

        final List<A2UIFunctionDefinition> result = new ArrayList<>();
        final Iterator<Map.Entry<String, JsonNode>> fields = functionsNode.fields();

        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> entry = fields.next();
            result.add(parseOneFunction(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static A2UIFunctionDefinition parseOneFunction(String name, JsonNode funcSchema) {
        final String description = funcSchema.has("description") ? funcSchema.get("description").asText() : "";

        // Parse returnType
        final String returnType = funcSchema.path("properties").path("returnType").path("const").asText("void");

        // Parse args
        final JsonNode argsSchema = funcSchema.path("properties").path("args");
        final Map<String, String> args = new LinkedHashMap<>();
        final JsonNode argsProps = argsSchema.path("properties");

        if (argsProps.isObject()) {
            argsProps.fields().forEachRemaining(arg -> {
                final String argType = resolveFieldType(arg.getValue());
                args.put(arg.getKey(), argType);
            });
        }

        // Parse required args
        final List<String> requiredArgs = new ArrayList<>();
        final JsonNode reqNode = argsSchema.path("required");
        if (reqNode.isArray()) {
            for (JsonNode r : reqNode) {
                requiredArgs.add(r.asText());
            }
        }

        return new A2UIFunctionDefinition(name, description, args, requiredArgs, returnType);
    }
}