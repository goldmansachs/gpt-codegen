package org.rj.modelgen.bpmn.intrep.model.rendering.boundaryevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.ErrorEventDefinition;
import org.camunda.bpm.model.bpmn.instance.EventDefinition;
import org.rj.modelgen.bpmn.intrep.model.BoundaryEventAttachment;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.List;
import java.util.function.Function;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.ERROR_BOUNDARY_EVENT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;
import static org.rj.modelgen.bpmn.intrep.model.common.BpmnDefinitionElementResolver.resolveOrCreateError;

public class ErrorBoundaryEventNode extends AbstractBoundaryEventNode {

    public ErrorBoundaryEventNode() {
        super();
        this.cancelActivity = true;
    }

    public ErrorBoundaryEventNode(String id, String name) {
        super(id, name, ERROR_BOUNDARY_EVENT);
        this.cancelActivity = true;
    }

    @JsonIgnore
    @Override
    protected void addEventDefinition(BoundaryEvent boundaryEvent) {
        var modelInstance = (BpmnModelInstance) boundaryEvent.getModelInstance();
        var errorEventDefinition = modelInstance.newInstance(ErrorEventDefinition.class);

        if (inputs != null) {
            findInput(ERROR_REF).ifPresent(input -> {
                String errorCode = input.getValue();
                if (errorCode != null && !errorCode.isBlank()) {
                    org.camunda.bpm.model.bpmn.instance.Error error = resolveOrCreateError(modelInstance, errorCode);
                    errorEventDefinition.setError(error);
                }
            });
        }

        boundaryEvent.addChildElement(errorEventDefinition);
    }

    public static void applyAttachmentEventDefinition(BoundaryEventAttachment event,
                                                       BoundaryEvent boundaryEvent,
                                                       BpmnModelInstance modelInstance,
                                                       Function<String, org.camunda.bpm.model.bpmn.instance.Error> errorResolver) {
        String errorCode = event.findInput(ERROR_REF).map(ElementNodeInput::getValue).orElse(null);
        if (errorCode != null && !errorCode.isBlank()) {
            org.camunda.bpm.model.bpmn.instance.Error error = errorResolver.apply(errorCode);
            var errorDef = modelInstance.newInstance(ErrorEventDefinition.class);
            errorDef.setError(error);
            boundaryEvent.addChildElement(errorDef);
        } else {
            boundaryEvent.addChildElement(modelInstance.newInstance(ErrorEventDefinition.class));
        }
    }

    public static void extractEventInputs(BoundaryEvent boundaryEvent, List<ElementNodeInput> inputs) {
        for (EventDefinition ed : boundaryEvent.getEventDefinitions()) {
            if (ed instanceof ErrorEventDefinition) {
                String errorRef = ed.getDomElement().getAttribute(ERROR_REF);
                if (errorRef != null) {
                    inputs.add(ElementNodeInput.createInputFromAttribute(ERROR_REF, errorRef, true));
                }
            }
        }
    }
}
