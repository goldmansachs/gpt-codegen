package org.rj.modelgen.bpmn.intrep.model.rendering.boundaryevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.bpmn.instance.Message;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.rj.modelgen.bpmn.intrep.model.BoundaryEventAttachment;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.MESSAGE_BOUNDARY_EVENT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;
import static org.rj.modelgen.bpmn.intrep.model.common.BpmnDefinitionElementResolver.resolveOrCreateMessage;

public class MessageBoundaryEventNode extends AbstractBoundaryEventNode {

    public MessageBoundaryEventNode() {
        super();
    }

    public MessageBoundaryEventNode(String id, String name) {
        super(id, name, MESSAGE_BOUNDARY_EVENT);
    }

    @JsonIgnore
    @Override
    protected void addEventDefinition(BoundaryEvent boundaryEvent) {
        var modelInstance = (BpmnModelInstance) boundaryEvent.getModelInstance();
        var messageEventDefinition = modelInstance.newInstance(MessageEventDefinition.class);

        if (inputs != null) {
            findInput(MESSAGE_REF).ifPresent(input -> {
                Message message = resolveOrCreateMessage(modelInstance, input.getValue());
                messageEventDefinition.setMessage(message);
            });
        }

        boundaryEvent.addChildElement(messageEventDefinition);
    }

    public static void applyAttachmentEventDefinition(BoundaryEventAttachment event,
                                                      BoundaryEvent boundaryEvent,
                                                      BpmnModelInstance modelInstance) {
        var messageDef = modelInstance.newInstance(MessageEventDefinition.class);
        event.findInput(MESSAGE_REF).ifPresent(input -> {
            Message message = resolveOrCreateMessage(modelInstance, input.getValue());
            messageDef.setMessage(message);
        });
        boundaryEvent.addChildElement(messageDef);
    }

    public static void extractEventInputs(BoundaryEvent boundaryEvent, List<ElementNodeInput> inputs) {
        for (EventDefinition ed : boundaryEvent.getEventDefinitions()) {
            if (ed instanceof MessageEventDefinition msgDef) {
                Message msg = msgDef.getMessage();
                if (msg != null) {
                    inputs.add(ElementNodeInput.createInputFromAttribute(MESSAGE_REF, msg.getName(), true));
                } else {
                    String ref = ed.getDomElement().getAttribute(MESSAGE_REF);
                    if (ref != null) inputs.add(ElementNodeInput.createInputFromAttribute(MESSAGE_REF, ref, true));
                }
            }
        }
    }
}
