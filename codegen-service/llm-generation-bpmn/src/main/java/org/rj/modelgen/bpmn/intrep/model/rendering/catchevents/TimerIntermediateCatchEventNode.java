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

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.TIMER_INTERMEDIATE_CATCH_EVENT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;

public class TimerIntermediateCatchEventNode extends ElementNode {

    public TimerIntermediateCatchEventNode() {
        super();
    }

    public TimerIntermediateCatchEventNode(String id, String name) {
        super(id, name, TIMER_INTERMEDIATE_CATCH_EVENT);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        var eventBuilder = builder.intermediateCatchEvent(id).name(name);
        var element = eventBuilder.getElement();
        var timerEventDefinition = element.getModelInstance().newInstance(TimerEventDefinition.class);
        if (inputs != null) {
            findInput(TIMER_DURATION).ifPresent(input -> {
                var duration = element.getModelInstance().newInstance(TimeDuration.class);
                duration.setTextContent(input.getValue());
                timerEventDefinition.setTimeDuration(duration);
            });
            findInput(TIMER_DATE).ifPresent(input -> {
                var timeDate = element.getModelInstance().newInstance(TimeDate.class);
                timeDate.setTextContent(input.getValue());
                timerEventDefinition.setTimeDate(timeDate);
            });
        }
        element.addChildElement(timerEventDefinition);
        configureTaskMetadata(element, namespace);
        return eventBuilder.done();
    }

    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRender(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        List<ElementNodeInput> inputs = new ArrayList<>(super.reverseRender(flowNode, namespace, componentLibrary, globalVariableLibrary));
        if (flowNode instanceof IntermediateCatchEvent catchEvent) {
            for (EventDefinition ed : catchEvent.getEventDefinitions()) {
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
