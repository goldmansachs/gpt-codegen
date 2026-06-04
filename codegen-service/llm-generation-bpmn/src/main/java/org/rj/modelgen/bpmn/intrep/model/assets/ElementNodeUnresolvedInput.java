package org.rj.modelgen.bpmn.intrep.model.assets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.rj.modelgen.llm.component.ComponentInputResolutionStrategy;
import org.rj.modelgen.llm.intrep.assets.NodeUnresolvedInput;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ElementNodeUnresolvedInput extends NodeUnresolvedInput {

    private String name;
    private String alias;
    private String defaultValue;
    private String inputKey;
    private String message;

    public ElementNodeUnresolvedInput() { }

    public ElementNodeUnresolvedInput(String nodeId, String elementType, String name, String alias, String value, String defaultValue, String inputKey, ComponentInputResolutionStrategy resolutionStrategy) {
        super(nodeId, elementType, value, resolutionStrategy);
        this.name = name;
        this.alias = alias;
        this.defaultValue = defaultValue;
        this.inputKey = inputKey;
        this.message = constructMessage();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getInputKey() {
        return inputKey;
    }

    public void setInputKey(String inputKey) {
        this.inputKey = inputKey;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @JsonIgnore
    public String constructMessage() {
        return switch (getResolutionStrategy()) {
            case USER_REQUIRED -> String.format("Input '%s' is required for element '%s' (%s) but missing in your process description and could not be inferred from context. Please provide a value.", name, getNodeId(), getType());
            case INFERRED_CONFIRM -> String.format(
                    "Input '%s' is required for element '%s' (%s) but is missing in your process description, therefore it was " +
                            (defaultValue != null
                                    ? "defaulted to '" + defaultValue
                                    : "inferred from context to be '" + getValue()) +
                            "'. Please review the provided value.",
                    name, getNodeId(), getType());
            default -> "";// Not needed for other resolution strategies, as they will be filled in with inferred values by LLM
        };
    }
}
