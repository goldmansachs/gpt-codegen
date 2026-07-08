package org.rj.modelgen.bpmn.intrep.model.rendering.endevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.EndEventBuilder;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.END_EVENT;

public class EndEventNode extends ElementNode {

    public EndEventNode() {
        super();
    }

    public EndEventNode(String id, String name) {
        super(id, name, END_EVENT);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        EndEventBuilder taskBuilder = builder.endEvent(id).name(name);
        EndEvent task = taskBuilder.getElement();
        configureTaskMetadata(task, namespace);
        return taskBuilder.done();
    }
}

