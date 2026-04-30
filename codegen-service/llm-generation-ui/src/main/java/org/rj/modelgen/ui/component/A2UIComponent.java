package org.rj.modelgen.ui.component;

import com.fasterxml.jackson.databind.JsonNode;
import org.rj.modelgen.llm.component.Component;

import java.util.List;

/**
 * Definition of a single A2UI component type (e.g. TextField, Button, Card).
 */
public class A2UIComponent extends Component {
    private final String componentType;
    private final String description;
    private final List<FieldSpec> requiredFields;
    private final List<FieldSpec> optionalFields;
    private final boolean checkable;  // supports checks/validation (Checkable mixin)

    public A2UIComponent(String componentType, String description, List<FieldSpec> requiredFields, List<FieldSpec> optionalFields, boolean checkable) {
        this.componentType = componentType;
        this.description = description;
        this.requiredFields = requiredFields;
        this.optionalFields = optionalFields;
        this.checkable = checkable;
    }

    // FieldSpec should capture enum constraints where applicable
    public record FieldSpec(
            String name,
            String type,           // e.g. "DynamicString", "ChildList", "ComponentId", "Action", "enum", "number", "boolean"
            String description,
            List<String> enumValues, // null if not an enum field
            JsonNode rawSchema       // nullable; present when field has nested structure to serialize
    ) {
        /** Backward-compatible constructor for call sites that don't supply rawSchema. */
        public FieldSpec(String name, String type, String description, List<String> enumValues) {
            this(name, type, description, enumValues, null);
        }
    }

    public boolean isCheckable() {
        return checkable;
    }

    public List<FieldSpec> getOptionalFields() {
        return optionalFields;
    }

    public List<FieldSpec> getRequiredFields() {
        return requiredFields;
    }

    public String getDescription() {
        return description;
    }

    public String getComponentType() {
        return componentType;
    }

    @Override
    public String defaultSerialize() {
        return "[SERIALISED PROMPT DATA PLACEHOLDER]";
    }

}
