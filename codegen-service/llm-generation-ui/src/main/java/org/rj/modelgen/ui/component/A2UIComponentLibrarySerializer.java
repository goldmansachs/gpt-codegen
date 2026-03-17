package org.rj.modelgen.ui.component;

import org.rj.modelgen.llm.component.ComponentLibrarySerializer;

import java.util.List;
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

        return "--- AVAILABLE COMPONENTS ---\n"
                + "Available components (use the \"component\" field to specify type):\n\n"
                + componentSection
                + "\n\n--- AVAILABLE FUNCTIONS ---\n"
                + "Available functions (used in FunctionCall objects):\n\n"
                + functionSection;
    }

    private String serializeComponents(List<A2UIComponent> components) {
        if (components == null || components.isEmpty()) return "(none)";

        return components.stream()
                .map(this::serializeOneComponent)
                .collect(Collectors.joining("\n\n"));
    }

    private String serializeOneComponent(A2UIComponent comp) {
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
            sb.append("  Required: ");
            sb.append(comp.getRequiredFields().stream()
                    .map(this::formatFieldSpec)
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
        }

        if (comp.getOptionalFields() != null && !comp.getOptionalFields().isEmpty()) {
            sb.append("  Optional: ");
            sb.append(comp.getOptionalFields().stream()
                    .map(this::formatFieldSpec)
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
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

    private String formatFieldSpec(A2UIComponent.FieldSpec field) {
        if (field.enumValues() != null && !field.enumValues().isEmpty()) {
            return field.name() + ": " + String.join("/", field.enumValues());
        }
        final var typeHint = field.type() != null ? " (" + field.type() + ")" : "";
        return field.name() + typeHint;
    }
}
