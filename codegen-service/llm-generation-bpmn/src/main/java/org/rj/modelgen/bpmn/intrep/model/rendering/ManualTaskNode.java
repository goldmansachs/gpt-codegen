package org.rj.modelgen.bpmn.intrep.model.rendering;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.ManualTaskBuilder;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.ManualTask;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;

import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.TASK_MANUAL_TASK;

public class ManualTaskNode extends ElementNode {
    public ManualTaskNode() {
        super();
    }

    public ManualTaskNode(String id, String name) {
        super(id, name, TASK_MANUAL_TASK);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        ManualTaskBuilder taskBuilder = builder.manualTask(id).name(name);
        ManualTask task = taskBuilder.getElement();
        configureTaskMetadata(task, namespace);

        return taskBuilder.done();
    }
}
