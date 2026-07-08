package org.rj.modelgen.bpmn.intrep.model.rendering.boundaryevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;

public abstract class AbstractBoundaryEventNode extends ElementNode {

    protected boolean cancelActivity = true;

    protected AbstractBoundaryEventNode() {
        super();
    }

    protected AbstractBoundaryEventNode(String id, String name, String elementType) {
        super(id, name, elementType);
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public boolean isCancelActivity() {
        return cancelActivity;
    }

    public void setCancelActivity(boolean cancelActivity) {
        this.cancelActivity = cancelActivity;
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(
            AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {

        BpmnModelInstance modelInstance = builder.done();
        FlowNode parentElement = builder.getElement();

        // Create the boundary event and attach it to the parent element
        BoundaryEvent boundaryEvent = modelInstance.newInstance(BoundaryEvent.class);
        boundaryEvent.setId(this.id);
        boundaryEvent.setName(this.name);
        boundaryEvent.setCancelActivity(this.cancelActivity);
        boundaryEvent.setAttachedTo((org.camunda.bpm.model.bpmn.instance.Activity) parentElement);

        // Delegate to subclass to add the specific event definition (timer, message, error, conditional)
        addEventDefinition(boundaryEvent);

        // Add the boundary event to the parent element's container
        parentElement.getParentElement().addChildElement(boundaryEvent);

        configureTaskMetadata(boundaryEvent, namespace);
        return modelInstance;
    }


    @JsonIgnore
    protected abstract void addEventDefinition(BoundaryEvent boundaryEvent);
}
