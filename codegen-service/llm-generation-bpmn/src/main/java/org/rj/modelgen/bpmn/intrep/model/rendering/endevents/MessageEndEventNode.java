package org.rj.modelgen.bpmn.intrep.model.rendering.endevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.instance.*;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.MESSAGE_END_EVENT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;
import static org.rj.modelgen.bpmn.intrep.model.common.BpmnDefinitionElementResolver.resolveOrCreateMessage;


public class MessageEndEventNode extends ElementNode {

    public MessageEndEventNode() {
        super();
    }

    public MessageEndEventNode(String id, String name) {
        super(id, name, MESSAGE_END_EVENT);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        var eventBuilder = builder.endEvent(id).name(name);
        var element = eventBuilder.getElement();
        BpmnModelInstance modelInstance = (BpmnModelInstance) element.getModelInstance();

        var messageEventDefinition = modelInstance.newInstance(MessageEventDefinition.class);
        if (inputs != null) {
            findInput(MESSAGE_REF).ifPresent(input -> {
                String messageName = input.getValue();
                Message message = resolveOrCreateMessage(modelInstance, messageName);
                messageEventDefinition.setMessage(message);
            });
        }
        element.addChildElement(messageEventDefinition);

        configureTaskMetadata(element, namespace);
        return eventBuilder.done();
    }

    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRender(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        List<ElementNodeInput> inputs = new ArrayList<>(super.reverseRender(flowNode, namespace, componentLibrary, globalVariableLibrary));
        if (flowNode instanceof EndEvent endEvent) {
            for (EventDefinition ed : endEvent.getEventDefinitions()) {
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
