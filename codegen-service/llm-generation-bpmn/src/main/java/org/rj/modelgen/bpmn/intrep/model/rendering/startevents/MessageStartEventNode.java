package org.rj.modelgen.bpmn.intrep.model.rendering.startevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.StartEventBuilder;
import org.camunda.bpm.model.bpmn.instance.*;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.MESSAGE_START_EVENT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;
import static org.rj.modelgen.bpmn.intrep.model.common.BpmnDefinitionElementResolver.resolveOrCreateMessage;

public class MessageStartEventNode extends ElementNode {

    public MessageStartEventNode() {
        super();
    }

    public MessageStartEventNode(String id, String name) {
        super(id, name, MESSAGE_START_EVENT);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        FlowNode element = builder.getElement();
        BpmnModelInstance modelInstance = (BpmnModelInstance) element.getModelInstance();

        // Set isInterrupting on the start event (relevant for event subprocesses)
        if (element instanceof StartEvent startEvent) {
            boolean interrupting = findInput(IS_INTERRUPTING)
                    .map(input -> Boolean.parseBoolean(input.getValue()))
                    .orElse(true);
            startEvent.setInterrupting(interrupting);
        }

        // Create <messageEventDefinition> and resolve/create the top-level <message> element
        var messageEventDefinition = modelInstance.newInstance(MessageEventDefinition.class);
        if (inputs != null) {
            findInput(MESSAGE_REF).ifPresent(input -> {
                String messageId = input.getValue();
                Message message = resolveOrCreateMessage(modelInstance, messageId);
                messageEventDefinition.setMessage(message);
            });
        }
        element.addChildElement(messageEventDefinition);

        configureTaskMetadata(element, namespace);
        return builder.done();
    }

    @JsonIgnore
    @Override
    public void configureEventSubProcessStart(StartEventBuilder builder, BpmnModelInstance modelInstance) {
        findInput(MESSAGE_REF).ifPresent(input -> builder.message(input.getValue()));
        boolean interrupting = findInput(IS_INTERRUPTING)
                .map(i -> Boolean.parseBoolean(i.getValue())).orElse(true);
        builder.interrupting(interrupting);
    }


    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRender(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        List<ElementNodeInput> inputs = new ArrayList<>(super.reverseRender(flowNode, namespace, componentLibrary, globalVariableLibrary));
        if (flowNode instanceof StartEvent startEvent) {
            addInputInReverseRender(inputs, IS_INTERRUPTING, String.valueOf(startEvent.isInterrupting()));
            for (EventDefinition ed : startEvent.getEventDefinitions()) {
                if (ed instanceof MessageEventDefinition msgDef) {
                    Message msg = msgDef.getMessage();
                    if (msg != null) {
                        addInputInReverseRender(inputs, MESSAGE_REF, msg.getName());
                    }
                }
            }
        }
        return inputs;
    }
}
