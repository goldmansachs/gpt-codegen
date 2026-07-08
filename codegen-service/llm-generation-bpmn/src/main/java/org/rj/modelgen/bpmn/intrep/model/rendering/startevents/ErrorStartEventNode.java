package org.rj.modelgen.bpmn.intrep.model.rendering.startevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.StartEventBuilder;
import org.camunda.bpm.model.bpmn.instance.ErrorEventDefinition;
import org.camunda.bpm.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.ERROR_START_EVENT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;

public class ErrorStartEventNode extends ElementNode {

    public ErrorStartEventNode() {
        super();
    }

    public ErrorStartEventNode(String id, String name) {
        super(id, name, ERROR_START_EVENT);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        FlowNode element = builder.getElement();
        var modelInstance = element.getModelInstance();

        // Error start events are always interrupting
        if (element instanceof StartEvent startEvent) {
            startEvent.setInterrupting(true);
        }

        // Create <errorEventDefinition> with optional errorRef
        var errorEventDefinition = modelInstance.newInstance(ErrorEventDefinition.class);
        if (inputs != null) {
            findInput(ERROR_REF).ifPresent(input ->
                    errorEventDefinition.setAttributeValue(ERROR_REF, input.getValue()));
        }
        element.addChildElement(errorEventDefinition);

        configureTaskMetadata(element, namespace);
        return builder.done();
    }

    @JsonIgnore
    @Override
    public void configureEventSubProcessStart(StartEventBuilder builder, BpmnModelInstance modelInstance) {
        findInput(ERROR_REF).ifPresent(input -> {
            // Resolve or create a top-level <error> element so errorRef is a valid reference
            for (org.camunda.bpm.model.bpmn.instance.Error existing : modelInstance.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Error.class)) {
                if (input.getValue().equals(existing.getErrorCode())) {
                    var errorDef = modelInstance.newInstance(ErrorEventDefinition.class);
                    errorDef.setError(existing);
                    builder.getElement().addChildElement(errorDef);
                    return;
                }
            }
            var definitions = modelInstance.getDefinitions();
            var error = modelInstance.newInstance(org.camunda.bpm.model.bpmn.instance.Error.class);
            error.setId(ERROR_PREFIX + java.util.UUID.randomUUID().toString().substring(0, 7));
            error.setErrorCode(input.getValue());
            error.setName(input.getValue());
            definitions.addChildElement(error);
            var errorDef = modelInstance.newInstance(ErrorEventDefinition.class);
            errorDef.setError(error);
            builder.getElement().addChildElement(errorDef);
        });
    }

    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRender(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        List<ElementNodeInput> inputs = new ArrayList<>(super.reverseRender(flowNode, namespace, componentLibrary, globalVariableLibrary));
        if (flowNode instanceof StartEvent startEvent) {
            for (EventDefinition ed : startEvent.getEventDefinitions()) {
                if (ed instanceof ErrorEventDefinition) {
                    String errorRef = ed.getDomElement().getAttribute(ERROR_REF);
                    addInputInReverseRender(inputs, ERROR_REF, errorRef);
                }
            }
        }
        return inputs;
    }
}
