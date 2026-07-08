package org.rj.modelgen.bpmn.intrep.model.rendering.endevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.instance.ErrorEventDefinition;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.ERROR_END_EVENT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;

public class ErrorEndEventNode extends ElementNode {

    public ErrorEndEventNode() {
        super();
    }

    public ErrorEndEventNode(String id, String name) {
        super(id, name, ERROR_END_EVENT);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        var eventBuilder = builder.endEvent(id).name(name);
        var element = eventBuilder.getElement();

        // Add error event definition with errorRef from inputs
        var errorEventDefinition = element.getModelInstance().newInstance(ErrorEventDefinition.class);
        if (inputs != null) {
            findInput(ERROR_REF).ifPresent(input -> errorEventDefinition.setAttributeValue(ERROR_REF, input.getValue()));
            findInput(ERROR_MESSAGE).ifPresent(input ->
                    element.setAttributeValueNs(namespace, ERROR_MESSAGE, input.getValue()));
        }
        element.addChildElement(errorEventDefinition);

        configureTaskMetadata(element, namespace);
        return eventBuilder.done();
    }

    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRender(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        List<ElementNodeInput> inputs = new ArrayList<>(super.reverseRender(flowNode, namespace, componentLibrary, globalVariableLibrary));
        if (flowNode instanceof EndEvent endEvent) {
            for (EventDefinition ed : endEvent.getEventDefinitions()) {
                if (ed instanceof ErrorEventDefinition) {
                    String errorRef = ed.getDomElement().getAttribute(ERROR_REF);
                    addInputInReverseRender(inputs, ERROR_REF, errorRef);
                }
            }
        }
        return inputs;
    }
}
