package org.rj.modelgen.ui.component;

import com.fasterxml.jackson.databind.JsonNode;
import org.rj.modelgen.llm.component.ComponentLibrarySerializer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serializes the A2UI component library (components + functions) into a single
 * string block suitable for injection into the prompt via a single substitution placeholder.
 */
public class A2UIComponentLibrarySerializer<TComponentLibrary extends A2UIComponentLibrary> implements ComponentLibrarySerializer<TComponentLibrary> {

    @Override
    public String serialize(A2UIComponentLibrary library) {
        final var componentSection = serializeComponents(library.getComponents());
        final var functionSection = serializeFunctions(library.getFunctions());
        final var commonTypesSection = A2UICommonTypeSerializer.serialize(library.getCommonTypesJson());

        final var sb = new StringBuilder();
        sb.append("--- AVAILABLE COMPONENTS ---\n")
          .append("Available components (use the \"component\" field to specify type):\n\n")
          .append(componentSection);

        if (!commonTypesSection.isEmpty()) {
            sb.append("\n\n--- COMMON TYPES ---\n")
              .append("Common type definitions used across A2UI schemas (from common_types.json):\n\n")
              .append(commonTypesSection);
        }

        sb.append("\n\n--- AVAILABLE FUNCTIONS ---\n")
          .append("Available functions (used in FunctionCall objects):\n\n")
          .append(functionSection);

        return sb.toString();
    }

    private String serializeComponents(List<A2UIComponent> components) {
        if (components == null || components.isEmpty()) return "(none)";

        return components.stream()
                .map(this::serializeOneComponent)
                .collect(Collectors.joining("\n\n"));
    }

    protected String serializeOneComponent(A2UIComponent comp) {
        final var sb = new StringBuilder();
        sb.append("**").append(comp.getComponentType()).append("**");
        if (comp.getDescription() != null) {
            sb.append(" — ").append(comp.getDescription());
        }
        if (comp.isCheckable()) {
            sb.append(" [Checkable]");
        }
        sb.append("\n");

        if (comp.getRequiredFields() != null && !comp.getRequiredFields().isEmpty()) {
            if (hasAnyComplexField(comp.getRequiredFields())) {
                sb.append("  Required:\n");
                comp.getRequiredFields().forEach(f -> appendFieldDetail(sb, f, 2));
            } else {
                sb.append("  Required: ");
                sb.append(comp.getRequiredFields().stream()
                        .map(this::formatFieldSpec)
                        .collect(Collectors.joining(", ")));
                sb.append("\n");
            }
        }

        if (comp.getOptionalFields() != null && !comp.getOptionalFields().isEmpty()) {
            if (hasAnyComplexField(comp.getOptionalFields())) {
                sb.append("  Optional:\n");
                comp.getOptionalFields().forEach(f -> appendFieldDetail(sb, f, 2));
            } else {
                sb.append("  Optional: ");
                sb.append(comp.getOptionalFields().stream()
                        .map(this::formatFieldSpec)
                        .collect(Collectors.joining(", ")));
                sb.append("\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    private String serializeFunctions(List<A2UIFunctionDefinition> functions) {
        if (functions == null || functions.isEmpty()) return "(none)";

        return functions.stream()
                .map(this::serializeOneFunction)
                .collect(Collectors.joining("\n\n"));
    }

    private String serializeOneFunction(A2UIFunctionDefinition func) {
        final var sb = new StringBuilder();
        sb.append("**").append(func.getName()).append("**");
        if (func.getDescription() != null) {
            sb.append(" — ").append(func.getDescription());
        }
        sb.append(" → ").append(func.getReturnType());
        sb.append("\n");

        if (func.getArgs() != null && !func.getArgs().isEmpty()) {
            String allArgs = func.getArgs().entrySet().stream()
                    .map(e -> e.getKey() + " (" + e.getValue() + ")")
                    .collect(Collectors.joining(", "));

            final var requiredNames = func.getRequiredArgs() != null
                    ? func.getRequiredArgs()
                    : List.<String>of();

            sb.append("  Args: ").append(allArgs).append("\n");
            if (!requiredNames.isEmpty()) {
                sb.append("  Required: ").append(String.join(", ", requiredNames)).append("\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    protected String formatFieldSpec(A2UIComponent.FieldSpec field) {
        if (field.enumValues() != null && !field.enumValues().isEmpty()) {
            return field.name() + ": " + String.join("/", field.enumValues());
        }
        final var typeHint = field.type() != null ? " (" + field.type() + ")" : "";
        return field.name() + typeHint;
    }

    private boolean hasAnyComplexField(List<A2UIComponent.FieldSpec> fields) {
        return fields.stream().anyMatch(f -> f.rawSchema() != null);
    }

    protected void appendFieldDetail(StringBuilder sb, A2UIComponent.FieldSpec field, int indent) {
        final String pad = "  ".repeat(indent);
        sb.append(pad).append("- ").append(field.name());
        sb.append(" (").append(field.type()).append(")");

        if (field.enumValues() != null && !field.enumValues().isEmpty()) {
            sb.append(" — values: ").append(field.enumValues().stream()
                    .map(v -> "\"" + v + "\"").collect(Collectors.joining(", ")));
        }
        if (field.description() != null && !field.description().isEmpty()) {
            sb.append(" — ").append(field.description());
        }
        sb.append("\n");

        if (field.rawSchema() != null) {
            serializeNestedFieldSchema(sb, field.rawSchema(), indent + 1);
        }
    }

    private void serializeNestedFieldSchema(StringBuilder sb, JsonNode schema, int indent) {
        final String pad = "  ".repeat(indent);

        if ("array".equals(schema.path("type").asText("")) && schema.has("items")) {
            JsonNode items = schema.get("items");

            // Items with enum constraint
            if (items.has("enum")) {
                sb.append(pad).append("Item values: ")
                  .append(A2UICommonTypeSerializer.joinEnumValues(items.get("enum")))
                  .append("\n");
                return;
            }

            // Items with properties (object items)
            if (items.has("properties")) {
                sb.append(pad).append("Each item:\n");
                A2UICommonTypeSerializer.serializeObjectProperties(sb, items, indent);

                if (items.has("additionalProperties") && !items.get("additionalProperties").asBoolean(true)) {
                    sb.append(pad).append("  additionalProperties: false\n");
                }
                return;
            }
        }

        if ("object".equals(schema.path("type").asText("")) && schema.has("properties")) {
            A2UICommonTypeSerializer.serializeObjectProperties(sb, schema, indent - 1);

            if (schema.has("additionalProperties") && !schema.get("additionalProperties").asBoolean(true)) {
                sb.append(pad).append("additionalProperties: false\n");
            }
        }
    }
}
