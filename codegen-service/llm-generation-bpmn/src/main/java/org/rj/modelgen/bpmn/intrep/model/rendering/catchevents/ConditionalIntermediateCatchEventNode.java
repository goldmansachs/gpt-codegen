package org.rj.modelgen.bpmn.intrep.model.rendering.catchevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.instance.*;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.CONDITIONAL_INTERMEDIATE_CATCH_EVENT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;

public class ConditionalIntermediateCatchEventNode extends ElementNode {

    public ConditionalIntermediateCatchEventNode() {
        super();
    }

    public ConditionalIntermediateCatchEventNode(String id, String name) {
        super(id, name, CONDITIONAL_INTERMEDIATE_CATCH_EVENT);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        var eventBuilder = builder.intermediateCatchEvent(id).name(name);
        var element = eventBuilder.getElement();
        var modelInstance = element.getModelInstance();
        var conditionalEventDefinition = modelInstance.newInstance(ConditionalEventDefinition.class);
        if (inputs != null) {
            findInput(CONDITION_EXPRESSION).ifPresent(input -> {
                var condition = modelInstance.newInstance(Condition.class);
                condition.setTextContent(input.getValue());
                conditionalEventDefinition.setCondition(condition);
            });
        }
        element.addChildElement(conditionalEventDefinition);
        configureTaskMetadata(element, namespace);
        return eventBuilder.done();
    }

    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRender(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        List<ElementNodeInput> inputs = new ArrayList<>(super.reverseRender(flowNode, namespace, componentLibrary, globalVariableLibrary));
        if (flowNode instanceof IntermediateCatchEvent catchEvent) {
            for (EventDefinition ed : catchEvent.getEventDefinitions()) {
                if (ed instanceof ConditionalEventDefinition condDef && condDef.getCondition() != null) {
                    addInputInReverseRender(inputs, CONDITION_EXPRESSION, condDef.getCondition().getTextContent());
                }
            }
        }
        return inputs;
    }
}
