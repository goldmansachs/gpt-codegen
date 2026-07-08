package org.rj.modelgen.bpmn.intrep.model.rendering.boundaryevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.ConditionExpression;
import org.camunda.bpm.model.bpmn.instance.ConditionalEventDefinition;
import org.camunda.bpm.model.bpmn.instance.EventDefinition;
import org.rj.modelgen.bpmn.intrep.model.BoundaryEventAttachment;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.CONDITIONAL_BOUNDARY_EVENT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;


public class ConditionalBoundaryEventNode extends AbstractBoundaryEventNode {

    public ConditionalBoundaryEventNode() {
        super();
    }

    public ConditionalBoundaryEventNode(String id, String name) {
        super(id, name, CONDITIONAL_BOUNDARY_EVENT);
    }

    @JsonIgnore
    @Override
    protected void addEventDefinition(BoundaryEvent boundaryEvent) {
        var modelInstance = boundaryEvent.getModelInstance();
        var conditionalEventDefinition = modelInstance.newInstance(ConditionalEventDefinition.class);

        if (inputs != null) {
            findInput(CONDITION_EXPRESSION).ifPresent(input -> {
                var condition = modelInstance.newInstance(ConditionExpression.class);
                condition.setTextContent(input.getValue());
                conditionalEventDefinition.addChildElement(condition);
            });
        }

        boundaryEvent.addChildElement(conditionalEventDefinition);
    }

    public static void applyAttachmentEventDefinition(BoundaryEventAttachment event,
                                                       BoundaryEvent boundaryEvent,
                                                       BpmnModelInstance modelInstance) {
        var conditionalDef = modelInstance.newInstance(ConditionalEventDefinition.class);
        event.findInput(CONDITION_EXPRESSION).ifPresent(input -> {
            var condition = modelInstance.newInstance(ConditionExpression.class);
            condition.setTextContent(input.getValue());
            conditionalDef.addChildElement(condition);
        });
        boundaryEvent.addChildElement(conditionalDef);
    }

    public static void extractEventInputs(BoundaryEvent boundaryEvent, List<ElementNodeInput> inputs) {
        for (EventDefinition ed : boundaryEvent.getEventDefinitions()) {
            if (ed instanceof ConditionalEventDefinition condDef && condDef.getCondition() != null) {
                inputs.add(ElementNodeInput.createInputFromAttribute(CONDITION_EXPRESSION, condDef.getCondition().getTextContent(), true));
            }
        }
    }
}
