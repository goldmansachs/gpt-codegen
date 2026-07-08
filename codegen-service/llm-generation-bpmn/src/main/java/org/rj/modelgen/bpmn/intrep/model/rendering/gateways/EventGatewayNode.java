package org.rj.modelgen.bpmn.intrep.model.rendering.gateways;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.EventBasedGatewayBuilder;
import org.camunda.bpm.model.bpmn.instance.EventBasedGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.GatewayConstants.TARGET_NODE_ID;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.GATEWAY_EVENT;

public class EventGatewayNode extends ElementNode {

    public EventGatewayNode() {
        super();
    }

    public EventGatewayNode(String id, String name) {
        super(id, name, GATEWAY_EVENT);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        EventBasedGatewayBuilder gatewayBuilder = builder.eventBasedGateway().id(id).name(name);
        EventBasedGateway gateway = gatewayBuilder.getElement();
        configureTaskMetadata(gateway, namespace);
        return gatewayBuilder.done();
    }
}
