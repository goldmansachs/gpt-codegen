package org.rj.modelgen.bpmn.intrep.model.rendering.startevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.START_EVENT;

public class StartEventNode extends ElementNode {

    public StartEventNode() {
        super();
    }

    public StartEventNode(String id, String name) {
        super(id, name, START_EVENT);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        // Start event is already created by the process builder; configure metadata using the provided namespace
        FlowNode element = builder.getElement();
        configureTaskMetadata(element, namespace);
        return builder.done();
    }
}
