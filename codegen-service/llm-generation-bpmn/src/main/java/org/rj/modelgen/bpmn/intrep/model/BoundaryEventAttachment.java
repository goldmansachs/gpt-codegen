package org.rj.modelgen.bpmn.intrep.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.BoundaryEventBuilder;
import org.camunda.bpm.model.bpmn.instance.*;
import org.rj.modelgen.bpmn.generation.BpmnConstants;
import org.rj.modelgen.bpmn.intrep.model.rendering.boundaryevents.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "name", "eventType", "cancelActivity", "inputs", "connectedTo"})
public class BoundaryEventAttachment {
    private static final Logger LOG = LoggerFactory.getLogger(BoundaryEventAttachment.class);
    private String id;
    private String name;
    private String eventType;
    private boolean cancelActivity = true;
    private List<ElementNodeInput> inputs;
    private List<ElementConnection> connectedTo;

    public BoundaryEventAttachment() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public boolean isCancelActivity() {
        return cancelActivity;
    }

    public void setCancelActivity(boolean cancelActivity) {
        this.cancelActivity = cancelActivity;
    }

    public List<ElementNodeInput> getInputs() {
        return inputs;
    }

    public void setInputs(List<ElementNodeInput> inputs) {
        this.inputs = inputs;
    }

    public List<ElementConnection> getConnectedTo() {
        return connectedTo;
    }

    public void setConnectedTo(List<ElementConnection> connectedTo) {
        this.connectedTo = connectedTo;
    }

    public Optional<ElementNodeInput> findInput(String inputName) {
        if (inputs == null || inputName == null) return Optional.empty();
        return inputs.stream()
                .filter(i -> inputName.equals(i.getName()))
                .findFirst();
    }

    public void applyEventDefinition(BoundaryEvent boundaryEvent, BpmnModelInstance modelInstance, Function<String, org.camunda.bpm.model.bpmn.instance.Error> errorResolver) {
            BoundaryEventType.fromTypeConstant(eventType)
                    .ifPresentOrElse(
                            type -> type.applyDefinition(this, boundaryEvent, modelInstance, errorResolver),
                            () -> LOG.warn("Unknown boundary event type: {}", eventType));
        }
}
