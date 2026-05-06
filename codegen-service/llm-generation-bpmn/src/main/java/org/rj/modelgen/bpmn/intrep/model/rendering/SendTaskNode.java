package org.rj.modelgen.bpmn.intrep.model.rendering;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.SendTaskBuilder;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.TASK_SEND_TASK;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SendTaskConstants.*;
import static org.rj.modelgen.bpmn.intrep.model.common.ElementNodeSharedUtils.getAttrName;

public class SendTaskNode extends ElementNode {

    public SendTaskNode() {
        super();
    }

    public SendTaskNode(String id, String name) {
        super(id, name, TASK_SEND_TASK);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        SendTaskBuilder taskBuilder = builder.sendTask(id).name(name);
        SendTask task = taskBuilder.getElement();
        configureTaskMetadata(task, namespace);

        if (inputs != null) {
            inputs.forEach(input -> {
                String attrName = getAttrName(input, elementDefinition);
                if (ATTRIBUTES.contains(input.getName())) {
                    task.setAttributeValueNs(namespace, attrName, input.getValue());
                } else if (EXTENSIONS.contains(input.getName())) {
                    ExtensionElements extensionElements = getExtensionElements(task);
                    ModelElementInstance extensionElement = extensionElements.addExtensionElement(namespace, attrName);
                    extensionElement.setTextContent(input.getValue());
                }
            });
        }

        return taskBuilder.done();
    }

    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRender(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        return super.reverseRender(flowNode, namespace, componentLibrary, globalVariableLibrary);
    }
}
