package org.rj.modelgen.bpmn.intrep.model.rendering.startevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.StartEventBuilder;
import org.camunda.bpm.model.bpmn.instance.*;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.CONDITIONAL_START_EVENT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;

public class ConditionalStartEventNode extends ElementNode {

    public ConditionalStartEventNode() {
        super();
    }

    public ConditionalStartEventNode(String id, String name) {
        super(id, name, CONDITIONAL_START_EVENT);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        FlowNode element = builder.getElement();
        var modelInstance = element.getModelInstance();

        // Set isInterrupting on the start event (relevant for event subprocesses)
        if (element instanceof StartEvent startEvent) {
            boolean interrupting = findInput(IS_INTERRUPTING)
                    .map(input -> Boolean.parseBoolean(input.getValue()))
                    .orElse(true);
            startEvent.setInterrupting(interrupting);
        }

        // Create <conditionalEventDefinition> with a <condition> child element
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
        return builder.done();
    }

    @JsonIgnore
    @Override
    public void configureEventSubProcessStart(StartEventBuilder builder, BpmnModelInstance modelInstance) {
        findInput(CONDITION_EXPRESSION).ifPresent(input -> {
            var conditionalDef = modelInstance.newInstance(ConditionalEventDefinition.class);
            var condition = modelInstance.newInstance(Condition.class);
            condition.setTextContent(input.getValue());
            conditionalDef.setCondition(condition);
            builder.getElement().addChildElement(conditionalDef);
        });
        boolean interrupting = findInput(IS_INTERRUPTING)
                .map(i -> Boolean.parseBoolean(i.getValue())).orElse(true);
        builder.interrupting(interrupting);
    }

    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRender(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        List<ElementNodeInput> inputs = new ArrayList<>(super.reverseRender(flowNode, namespace, componentLibrary, globalVariableLibrary));
        if (flowNode instanceof StartEvent startEvent) {
            addInputInReverseRender(inputs, IS_INTERRUPTING, String.valueOf(startEvent.isInterrupting()));
            for (EventDefinition ed : startEvent.getEventDefinitions()) {
                if (ed instanceof ConditionalEventDefinition condDef) {
                    if (condDef.getCondition() != null) {
                        addInputInReverseRender(inputs, CONDITION_EXPRESSION, condDef.getCondition().getTextContent());
                    }
                }
            }
        }
        return inputs;
    }
}
