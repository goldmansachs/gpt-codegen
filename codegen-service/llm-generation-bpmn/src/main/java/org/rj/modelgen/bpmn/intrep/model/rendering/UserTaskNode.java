package org.rj.modelgen.bpmn.intrep.model.rendering;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.UserTaskBuilder;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;

import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.TASK_USER_TASK;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.UserTaskConstants.*;
import static org.rj.modelgen.bpmn.intrep.model.common.ElementNodeSharedUtils.getAttrName;

public class UserTaskNode extends ElementNode {

    public UserTaskNode() {
        super();
    }

    public UserTaskNode(String id, String name) {
        super(id, name, TASK_USER_TASK);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        UserTaskBuilder taskBuilder = builder.userTask(id).name(name);
        UserTask task = taskBuilder.getElement();
        configureTaskMetadata(task, namespace);

        if (inputs != null) {
            inputs.forEach(input -> {
                if (ATTRIBUTES.contains(input.getName())) {
                    String attrName = getAttrName(input, elementDefinition);
                    task.setAttributeValueNs(namespace, attrName, input.getValue());
                }
            });
        }

        return taskBuilder.done();
    }
}
