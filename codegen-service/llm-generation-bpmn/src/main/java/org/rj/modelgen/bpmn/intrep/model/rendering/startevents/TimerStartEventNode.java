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

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.TIMER_START_EVENT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;

public class TimerStartEventNode extends ElementNode {

    public TimerStartEventNode() {
        super();
    }

    public TimerStartEventNode(String id, String name) {
        super(id, name, TIMER_START_EVENT);
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

        // Create <timerEventDefinition> with either <timeDuration> or <timeDate>
        var timerEventDefinition = modelInstance.newInstance(TimerEventDefinition.class);
        if (inputs != null) {
            findInput(TIMER_DURATION).ifPresent(input -> {
                var duration = modelInstance.newInstance(TimeDuration.class);
                duration.setTextContent(input.getValue());
                timerEventDefinition.setTimeDuration(duration);
            });
            findInput(TIMER_DATE).ifPresent(input -> {
                var timeDate = modelInstance.newInstance(TimeDate.class);
                timeDate.setTextContent(input.getValue());
                timerEventDefinition.setTimeDate(timeDate);
            });
        }
        element.addChildElement(timerEventDefinition);

        configureTaskMetadata(element, namespace);
        return builder.done();
    }

    @JsonIgnore
    @Override
    public void configureEventSubProcessStart(StartEventBuilder builder, BpmnModelInstance modelInstance) {
        findInput(TIMER_DURATION).ifPresent(input -> builder.timerWithDuration(input.getValue()));
        findInput(TIMER_DATE).ifPresent(input -> builder.timerWithDate(input.getValue()));
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
                if (ed instanceof TimerEventDefinition timerDef) {
                    if (timerDef.getTimeDuration() != null) {
                        addInputInReverseRender(inputs, TIMER_DURATION, timerDef.getTimeDuration().getTextContent());
                    }
                    if (timerDef.getTimeDate() != null) {
                        addInputInReverseRender(inputs, TIMER_DATE, timerDef.getTimeDate().getTextContent());
                    }
                }
            }
        }
        return inputs;
    }
}
