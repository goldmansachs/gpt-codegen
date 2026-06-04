package org.rj.modelgen.bpmn.intrep.model.rendering;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.InclusiveGatewayBuilder;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementConnection;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.GatewayConstants.*;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.GatewayConstants.CONDITION_EXPRESSION;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.GATEWAY_INCLUSIVE;

public class InclusiveGatewayNode extends ElementNode implements ConditionalGateway {

    private static final Logger LOG = LoggerFactory.getLogger(InclusiveGatewayNode.class);

    public InclusiveGatewayNode() {
        super();
    }

    public InclusiveGatewayNode(String id, String name) {
        super(id, name, GATEWAY_INCLUSIVE);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        InclusiveGatewayBuilder gatewayBuilder = builder.inclusiveGateway(id).name(name);
        InclusiveGateway gateway = gatewayBuilder.getElement();
        configureTaskMetadata(gateway, namespace);;
        return gatewayBuilder.done();
    }

    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRender(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        return ConditionalGateway.reverseRenderConditionalInputs(flowNode);
    }

    @JsonIgnore
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> void renderConditionalConnections(AbstractFlowNodeBuilder<B, E> builder, ElementConnection connection, String connectionId, B outboundConnection) {
        String defaultTargetNodeId = getDefaultTargetNodeId();
        Map<String, String> conditions = getConditions();

        // Set default sequence flow on the gateway node
        if (connection.getTargetNode().equals(defaultTargetNodeId)) {
            builder.getElement().setAttributeValue(DEFAULT, connectionId);
        }

        outboundConnection.condition(connection.getDescription(), conditions.getOrDefault(connection.getTargetNode(), ""));
    }

    // Convenience methods
    @JsonIgnore
    @Override
    public String getUniqueElementIdName() {
        return TARGET_NODE_ID;
    }


    @JsonIgnore
    public String getDefaultTargetNodeId() {
        return findInput(DEFAULT)
                .map(ElementNodeInput::getValue)
                .orElse("");
    }

    @JsonIgnore
    public Map<String, String> getConditions() {
        Map<String, String> conditionsMap = new HashMap<>();

        for (ElementNodeInput condition : findAllInputs(CONDITIONS)) {
            var targetNodeId = condition.findProperty(TARGET_NODE_ID);
            var conditionExpression = condition.findProperty(CONDITION_EXPRESSION);

            if (targetNodeId.isPresent() && conditionExpression.isPresent()) {
                conditionsMap.put(targetNodeId.get().getValue(), conditionExpression.get().getValue());
            }
        }

        return conditionsMap;
    }
}
