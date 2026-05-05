package org.rj.modelgen.bpmn.intrep.model.rendering;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.ReceiveTaskBuilder;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.TASK_RECEIVE_TASK;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.ReceiveTaskConstants.*;

public class ReceiveTaskNode extends ElementNode {

    public ReceiveTaskNode() {
        super();
    }

    public ReceiveTaskNode(String id, String name) {
        super(id, name, TASK_RECEIVE_TASK);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        ReceiveTaskBuilder taskBuilder = builder.receiveTask(id).name(name);
        ReceiveTask task = taskBuilder.getElement();
        configureTaskMetadata(task, namespace);
        BpmnModelInstance modelInstance = (BpmnModelInstance) task.getModelInstance();

        String messageId = findInput(MESSAGE_ID_INPUT_NAME)
                .map(ElementNodeInput::getValue)
                .orElse(null);

        if (messageId != null) {
            Message message = modelInstance.getModelElementById(messageId);
            if (message == null) {
                Definitions definitions = modelInstance.getDefinitions();
                message = modelInstance.newInstance(Message.class);
                String randomId = java.util.UUID.randomUUID().toString().substring(0, 7);
                message.setId(MESSAGE_PREFIX + randomId);
                message.setName(messageId);

                definitions.addChildElement(message);
            }
            task.setMessage(message);
        }

        // Set availability script
        String availability = findInput(AVAILABILITY_INPUT_NAME)
                .map(ElementNodeInput::getValue)
                .orElse("");

        task.setAttributeValueNs(namespace, AVAILABILITY_ATTR, availability);

        return taskBuilder.done();
    }

    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRender(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        return super.reverseRender(flowNode, namespace, componentLibrary, globalVariableLibrary);
    }

    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRenderValues(DomElement dom,  FlowNode flowNode, String namespace,BpmnComponent.InputVariable iv) {
        if (MESSAGE_ID_INPUT_NAME.equals(iv.getName()) && flowNode instanceof ReceiveTask receiveTask) {
            List<ElementNodeInput> result = new ArrayList<>();
            Message message = receiveTask.getMessage();
            if (message != null && message.getName() != null) {
                result.add(ElementNodeInput.createConstant(MESSAGE_ID_INPUT_NAME, message.getName()));
            }
            return result;
        }
        return super.reverseRenderValues(dom, flowNode, namespace, iv);
    }
}
