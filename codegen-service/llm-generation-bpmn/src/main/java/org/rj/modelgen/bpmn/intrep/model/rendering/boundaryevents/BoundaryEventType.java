package org.rj.modelgen.bpmn.intrep.model.rendering.boundaryevents;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.Error;
import org.rj.modelgen.bpmn.intrep.model.BoundaryEventAttachment;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.*;

public enum BoundaryEventType {
    TIMER(TIMER_BOUNDARY_EVENT, TimerEventDefinition.class) {
        @Override
        public void extractInputs(BoundaryEvent boundaryEvent, List<ElementNodeInput> inputs) {
            TimerBoundaryEventNode.extractEventInputs(boundaryEvent, inputs);
        }
        @Override
        public void applyDefinition(BoundaryEventAttachment boundaryAttachment, BoundaryEvent boundaryEvent, BpmnModelInstance modelInstance, Function<String, Error> errorResolver) {
            TimerBoundaryEventNode.applyAttachmentEventDefinition(boundaryAttachment, boundaryEvent, modelInstance);
        }
    },
    MESSAGE(MESSAGE_BOUNDARY_EVENT, MessageEventDefinition.class) {
        @Override
        public void extractInputs(BoundaryEvent boundaryEvent, List<ElementNodeInput> inputs) {
            MessageBoundaryEventNode.extractEventInputs(boundaryEvent, inputs);
        }
        @Override
        public void applyDefinition(BoundaryEventAttachment boundaryAttachment, BoundaryEvent boundaryEvent, BpmnModelInstance modelInstance, Function<String, Error> errorResolver) {
            MessageBoundaryEventNode.applyAttachmentEventDefinition(boundaryAttachment, boundaryEvent, modelInstance);
        }
    },
    ERROR(ERROR_BOUNDARY_EVENT, ErrorEventDefinition.class) {
        @Override
        public void extractInputs(BoundaryEvent boundaryEvent, List<ElementNodeInput> inputs) {
            ErrorBoundaryEventNode.extractEventInputs(boundaryEvent, inputs);
        }
        @Override
        public void applyDefinition(BoundaryEventAttachment boundaryAttachment, BoundaryEvent boundaryEvent, BpmnModelInstance modelInstance, Function<String, Error> errorResolver) {
            ErrorBoundaryEventNode.applyAttachmentEventDefinition(boundaryAttachment, boundaryEvent, modelInstance, errorResolver);
        }
    },
    CONDITIONAL(CONDITIONAL_BOUNDARY_EVENT, ConditionalEventDefinition.class) {
        @Override
        public void extractInputs(BoundaryEvent boundaryEvent, List<ElementNodeInput> inputs) {
            ConditionalBoundaryEventNode.extractEventInputs(boundaryEvent, inputs);
        }
        @Override
        public void applyDefinition(BoundaryEventAttachment boundaryAttachment, BoundaryEvent boundaryEvent, BpmnModelInstance modelInstance, Function<String, Error> errorResolver) {
            ConditionalBoundaryEventNode.applyAttachmentEventDefinition(boundaryAttachment, boundaryEvent, modelInstance);
        }
    };

    private final String typeConstant;
    private final Class<? extends EventDefinition> eventDefinitionClass;

    BoundaryEventType(String typeConstant, Class<? extends EventDefinition> eventDefinitionClass) {
        this.typeConstant = typeConstant;
        this.eventDefinitionClass = eventDefinitionClass;
    }

    public String getTypeConstant() { return typeConstant; }

    public abstract void extractInputs(BoundaryEvent boundaryEvent, List<ElementNodeInput> inputs);

    public abstract void applyDefinition(BoundaryEventAttachment boundaryAttachment, BoundaryEvent boundaryEvent, BpmnModelInstance modelInstance, Function<String, org.camunda.bpm.model.bpmn.instance.Error> errorResolver);

    public static Optional<BoundaryEventType> fromEventDefinition(EventDefinition ed) {
        for (BoundaryEventType t : values()) {
            if (t.eventDefinitionClass.isInstance(ed)) return Optional.of(t);
        }
        return Optional.empty();
    }

    public static Optional<BoundaryEventType> fromTypeConstant(String typeConstant) {
        for (BoundaryEventType t : values()) {
            if (t.typeConstant.equals(typeConstant)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
