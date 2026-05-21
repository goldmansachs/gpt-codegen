package org.rj.modelgen.bpmn.intrep.model.rendering;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.ExclusiveGatewayBuilder;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementConnection;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.GATEWAY_EXCLUSIVE;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.GatewayConstants.*;

public class ExclusiveGatewayNode extends ElementNode implements ConditionalGateway {
    private static final Logger LOG = LoggerFactory.getLogger(ExclusiveGatewayNode.class);

    public ExclusiveGatewayNode() {
        super();
    }

    public ExclusiveGatewayNode(String id, String name) {
        super(id, name, GATEWAY_EXCLUSIVE);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        ExclusiveGatewayBuilder gatewayBuilder = builder.exclusiveGateway(id).name(name);
        ExclusiveGateway gateway = gatewayBuilder.getElement();
        configureTaskMetadata(gateway, namespace);
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

        // Set default sequence flow on the gateway node only if all conditions do not have an expression
        boolean allConditionsHaveExpressions = conditions.size() > 1 &&
                conditions.values().stream().allMatch(expr -> expr != null && !expr.isBlank());

        if (!allConditionsHaveExpressions && connection.getTargetNode().equals(defaultTargetNodeId)) {
            builder.getElement().setAttributeValue(DEFAULT, connectionId);
        }

        outboundConnection.condition(connection.getDescription(), conditions.getOrDefault(connection.getTargetNode(), ""));
    }

    // Convenience methods
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
