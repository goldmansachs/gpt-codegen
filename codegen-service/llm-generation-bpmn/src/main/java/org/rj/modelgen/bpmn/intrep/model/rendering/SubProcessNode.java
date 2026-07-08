package org.rj.modelgen.bpmn.intrep.model.rendering;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.generation.BpmnConstants;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.*;

public class SubProcessNode extends ElementNode {
    private static final Logger LOG = LoggerFactory.getLogger(SubProcessNode.class);

    public SubProcessNode() {
        super();
    }

    public SubProcessNode(String id, String name) {
        super(id, name, SUBPROCESS);
    }

    @JsonIgnore
    public boolean isTriggeredByEvent() {
        return findInput(TRIGGERED_BY_EVENT)
                .map(input -> Boolean.parseBoolean(input.getValue()))
                .orElse(false);
    }

    @JsonIgnore
    public String getSubProcessName() {
        return findInput(SUBPROCESS_NAME)
                .map(ElementNodeInput::getValue)
                .orElse(name);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        var spBuilder = builder.subProcess(id);
        if (name != null) {
            spBuilder.name(name);
        }
        return spBuilder.done();
    }

   @JsonIgnore
    public void configure(SubProcess subProcessElement, BpmnComponent elementDefinition, String namespace) {
        if (subProcessElement == null) return;

        // Set triggeredByEvent flag
        subProcessElement.setTriggeredByEvent(isTriggeredByEvent());

        // Set subprocess name
        String spName = getSubProcessName();
        if (spName != null) {
            subProcessElement.setName(spName);
        } else {
            LOG.warn("SubProcess '{}' has no name configured, so the model may not render as intended", id);
        }

        // Apply description from inputs if available
        findInput(SUBPROCESS_DESCRIPTION).ifPresent(input ->
                subProcessElement.setAttributeValueNs(namespace, SUBPROCESS_DESCRIPTION, input.getValue()));

        // Apply remaining attributes from component definition
        if (inputs != null && elementDefinition != null) {
            var attributes = BpmnConstants.SubProcessConfigConstants.ATTRIBUTES;
            inputs.forEach(input -> {
                if (attributes.contains(input.getName())) {
                    String attrName = org.rj.modelgen.bpmn.intrep.model.common.ElementNodeSharedUtils.getAttrName(input, elementDefinition);
                    subProcessElement.setAttributeValueNs(namespace, attrName, input.getValue());
                }
            });
        }

        // Store node metadata for lossless reverse rendering
        configureTaskMetadata(subProcessElement, namespace);
    }
}
