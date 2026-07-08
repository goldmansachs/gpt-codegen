package org.rj.modelgen.bpmn.intrep.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubProcessConfig {

    private String subProcessId;
    private String subProcessName;
    private String subProcessDescription;
    private Boolean triggeredByEvent;
    private IterationConfig iterationConfig;

    public SubProcessConfig() {
    }

    public SubProcessConfig(String subProcessId, String subProcessName, Boolean triggeredByEvent) {
        this.subProcessId = subProcessId;
        this.subProcessName = subProcessName;
        this.triggeredByEvent = triggeredByEvent;
    }

    public void populateFromNode(ElementNode node) {
        if (node == null || node.getInputs() == null) return;
        node.getInputs().stream()
                .filter(input -> input.getName() != null && input.getValue() != null)
                .forEach(this::applyConfigInput);
    }

    private void applyConfigInput(ElementNodeInput input) {
        switch (input.getName()) {
            case SUBPROCESS_ID:
                if (subProcessId == null) setSubProcessId(input.getValue());
                break;
            case SUBPROCESS_NAME:
                if (subProcessName == null) setSubProcessName(input.getValue());
                break;
            case SUBPROCESS_DESCRIPTION:
                if (subProcessDescription == null) setSubProcessDescription(input.getValue());
                break;
            case TRIGGERED_BY_EVENT:
                if (triggeredByEvent == null) setTriggeredByEvent(Boolean.parseBoolean(input.getValue()));
                break;
            default:
                break; // ignore unknown inputs
        }
    }

    public void applyDefaults(String id) {
        if (subProcessId == null) subProcessId = id;
        if (subProcessName == null) subProcessName = id;
        if (triggeredByEvent == null) triggeredByEvent = false;
    }

    public String getSubProcessId() {
        return subProcessId;
    }

    public void setSubProcessId(String subProcessId) {
        this.subProcessId = subProcessId;
    }

    public String getSubProcessName() {
        return subProcessName;
    }

    public void setSubProcessName(String subProcessName) {
        this.subProcessName = subProcessName;
    }

    public String getSubProcessDescription() {
        return subProcessDescription;
    }

    public void setSubProcessDescription(String subProcessDescription) {
        this.subProcessDescription = subProcessDescription;
    }

    @JsonProperty("triggeredByEvent")
    public Boolean getTriggeredByEvent() {
        return triggeredByEvent;
    }

    public void setTriggeredByEvent(Boolean triggeredByEvent) {
        this.triggeredByEvent = triggeredByEvent;
    }

    public IterationConfig getIterationConfig() {
        return iterationConfig;
    }

    public void setIterationConfig(IterationConfig iterationConfig) {
        this.iterationConfig = iterationConfig;
    }

    @JsonIgnore
    public boolean isTriggeredByEvent() {
        return Boolean.TRUE.equals(triggeredByEvent);
    }

    @JsonIgnore
    public boolean isConfigured() {
        return subProcessId != null || subProcessName != null;
    }
}

