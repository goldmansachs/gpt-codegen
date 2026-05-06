package org.rj.modelgen.bpmn.intrep.model.rendering;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.instance.ConditionExpression;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.rj.modelgen.bpmn.intrep.model.ElementConnection;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.GatewayConstants.*;

public interface ConditionalGateway {
    @JsonIgnore
    String getDefaultTargetNodeId();

    @JsonIgnore
    Map<String, String> getConditions();

    @JsonIgnore
    <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> void renderConditionalConnections(
            AbstractFlowNodeBuilder<B, E> builder,
            ElementConnection connection,
            String connectionId,
            B outboundConnection
    );

    @JsonIgnore
    static List<ElementNodeInput> reverseRenderConditionalInputs(FlowNode flowNode) {
        List<ElementNodeInput> inputs = new ArrayList<>();

        String defaultAttrValue = flowNode.getDomElement().getAttribute(DEFAULT);
        String defaultTargetNodeId = null;
        if (defaultAttrValue != null) {
            for (SequenceFlow sf : flowNode.getOutgoing()) {
                if (defaultAttrValue.equals(sf.getId())) {
                    defaultTargetNodeId = sf.getTarget().getId();
                    break;
                }
            }
        }

        if (defaultTargetNodeId != null) {
            ElementNodeInput defaultInput = new ElementNodeInput();
            defaultInput.setName(DEFAULT);
            defaultInput.setValue(defaultTargetNodeId);
            defaultInput.setVariableSource("CONSTANT");
            defaultInput.setIsProvided(true);
            inputs.add(defaultInput);
        }

        // 'conditions' input for each outgoing sequence flow
        for (SequenceFlow sf : flowNode.getOutgoing()) {
            String targetId = sf.getTarget().getId();
            String conditionExpr = "";
            ConditionExpression ce = sf.getConditionExpression();
            if (ce != null) {
                conditionExpr = ce.getTextContent() != null ? ce.getTextContent() : "";
            }

            ElementNodeInput targetNodeIdProp = ElementNodeInput.createInputFromAttribute(TARGET_NODE_ID, targetId, true);
            ElementNodeInput conditionExprProp = ElementNodeInput.createInputFromAttribute(CONDITION_EXPRESSION, conditionExpr, true);

            ElementNodeInput conditionInput = new ElementNodeInput();
            conditionInput.setName(CONDITIONS);
            conditionInput.setProperties(List.of(targetNodeIdProp, conditionExprProp));
            inputs.add(conditionInput);
        }

        return inputs;
    }
}
