package org.rj.modelgen.bpmn.generation.render;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.CompletionCondition;
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.rj.modelgen.bpmn.generation.BpmnConstants;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.IterationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.camunda.bpm.model.bpmn.impl.BpmnModelConstants.ACTIVITI_NS;

public class BpmnMultiInstanceRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(BpmnMultiInstanceRenderer.class);

    public BpmnMultiInstanceRenderer() {
    }

    public void applyIfRepeatable(ElementNode element, BpmnModelInstance modelInstance, String customNamespaceUri) {
        if (!element.isRepeatable()) return;

        ModelElementInstance elementInstance = modelInstance.getModelElementById(element.getId());
        if (!(elementInstance instanceof Activity activity)) {
            LOG.warn("Node '{}' is marked as repeatable but is not an Activity — multi-instance will not be applied", element.getId());
            return;
        }

        applyToActivity(element.getIterationConfig(), activity, modelInstance, customNamespaceUri);
    }

    public void applyToActivity(IterationConfig config, Activity activity, BpmnModelInstance modelInstance, String customNamespaceUri) {
        if (config == null || !config.isConfigured()) return;

        MultiInstanceLoopCharacteristics multiInstanceLoop = modelInstance.newInstance(MultiInstanceLoopCharacteristics.class);
        multiInstanceLoop.setAttributeValueNs(ACTIVITI_NS, BpmnConstants.MultiInstanceConstants.MULTI_INSTANCE_COLLECTION, config.getList());
        multiInstanceLoop.setAttributeValueNs(ACTIVITI_NS, BpmnConstants.MultiInstanceConstants.MULTI_INSTANCE_ELEMENT_VARIABLE, config.getItemInList());

        if (config.getItemLabel() != null && !config.getItemLabel().isBlank() && customNamespaceUri != null) {
            multiInstanceLoop.setAttributeValueNs(customNamespaceUri, BpmnConstants.MultiInstanceConstants.MULTI_INSTANCE_DISPLAY_LABEL, config.getItemLabel());
        }

        if (config.getCompletionCondition() != null && !config.getCompletionCondition().isBlank()) {
            CompletionCondition cc = modelInstance.newInstance(CompletionCondition.class);
            cc.setTextContent(config.getCompletionCondition());
            multiInstanceLoop.setCompletionCondition(cc);
        }

        activity.setLoopCharacteristics(multiInstanceLoop);
    }
}
