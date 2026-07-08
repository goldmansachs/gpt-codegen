package org.rj.modelgen.bpmn.intrep.model.rendering.boundaryevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.rj.modelgen.bpmn.intrep.model.BoundaryEventAttachment;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.TIMER_BOUNDARY_EVENT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;


public class TimerBoundaryEventNode extends AbstractBoundaryEventNode {

    public TimerBoundaryEventNode() {
        super();
    }

    public TimerBoundaryEventNode(String id, String name) {
        super(id, name, TIMER_BOUNDARY_EVENT);
    }

    @JsonIgnore
    @Override
    protected void addEventDefinition(BoundaryEvent boundaryEvent) {
        var modelInstance = (BpmnModelInstance) boundaryEvent.getModelInstance();
        var timerEventDefinition = modelInstance.newInstance(TimerEventDefinition.class);

        if (inputs != null) {
            var durationInput = findInput(TIMER_DURATION);
            if (durationInput.isPresent()) {
                var duration = modelInstance.newInstance(TimeDuration.class);
                duration.setTextContent(durationInput.get().getValue());
                timerEventDefinition.setTimeDuration(duration);
            }
            var dateInput = findInput(TIMER_DATE);
            if (dateInput.isPresent()) {
                var timeDate = modelInstance.newInstance(TimeDate.class);
                timeDate.setTextContent(dateInput.get().getValue());
                timerEventDefinition.setTimeDate(timeDate);
            }
        }

        boundaryEvent.addChildElement(timerEventDefinition);
    }

    public static void applyAttachmentEventDefinition(BoundaryEventAttachment event,
                                                      BoundaryEvent boundaryEvent,
                                                      BpmnModelInstance modelInstance) {
        var timerEventDefinition = modelInstance.newInstance(TimerEventDefinition.class);


        var durationInput = event.findInput(TIMER_DURATION);
        if (durationInput.isPresent()) {
            var duration = modelInstance.newInstance(TimeDuration.class);
            duration.setTextContent(durationInput.get().getValue());
            timerEventDefinition.setTimeDuration(duration);
        }
        var dateInput = event.findInput(TIMER_DATE);
        if (dateInput.isPresent()) {
            var timeDate = modelInstance.newInstance(TimeDate.class);
            timeDate.setTextContent(dateInput.get().getValue());
            timerEventDefinition.setTimeDate(timeDate);
        }
        var cycleInput = event.findInput(TIMER_CYCLE);
        if (cycleInput.isPresent()) {
            var timeCycle = modelInstance.newInstance(TimeCycle.class);
            timeCycle.setTextContent(cycleInput.get().getValue());
            timerEventDefinition.setTimeCycle(timeCycle);
        }

        boundaryEvent.addChildElement(timerEventDefinition);
    }

    public static void extractEventInputs(BoundaryEvent boundaryEvent, List<ElementNodeInput> inputs) {
        for (EventDefinition ed : boundaryEvent.getEventDefinitions()) {
            if (ed instanceof TimerEventDefinition timerDef) {
                if (timerDef.getTimeDuration() != null) {
                    inputs.add(ElementNodeInput.createInputFromAttribute(TIMER_DURATION, timerDef.getTimeDuration().getTextContent(), true));
                }
                if (timerDef.getTimeDate() != null) {
                    inputs.add(ElementNodeInput.createInputFromAttribute(TIMER_DATE, timerDef.getTimeDate().getTextContent(), true));
                }
                if (timerDef.getTimeCycle() != null) {
                    inputs.add(ElementNodeInput.createInputFromAttribute(TIMER_CYCLE, timerDef.getTimeCycle().getTextContent(), true));
                }
            }
        }
    }
}
