package org.rj.modelgen.bpmn.intrep.model.rendering.endevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.TERMINATE_END_EVENT;

public class TerminateEndEventNode extends ElementNode {

    public TerminateEndEventNode() {
        super();
    }

    public TerminateEndEventNode(String id, String name) {
        super(id, name, TERMINATE_END_EVENT);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        var eventBuilder = builder.endEvent(id).name(name);
        var element = eventBuilder.getElement();
        // Add terminate event definition
        var terminateEventDefinition = element.getModelInstance().newInstance(org.camunda.bpm.model.bpmn.instance.TerminateEventDefinition.class);
        element.addChildElement(terminateEventDefinition);
        configureTaskMetadata(element, namespace);
        return eventBuilder.done();
    }
}

