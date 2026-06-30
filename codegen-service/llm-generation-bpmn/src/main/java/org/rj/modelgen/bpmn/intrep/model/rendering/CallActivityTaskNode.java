package org.rj.modelgen.bpmn.intrep.model.rendering;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.CallActivityBuilder;
import org.camunda.bpm.model.bpmn.instance.CallActivity;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaIn;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaOut;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.CallActivityTaskConstants.*;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.TASK_CALL_ACTIVITY_TASK;

public class CallActivityTaskNode extends ElementNode {

    public CallActivityTaskNode() {
        super();
    }

    public CallActivityTaskNode(String id, String name) {
        super(id, name, TASK_CALL_ACTIVITY_TASK);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(
            AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {

        CallActivityBuilder taskBuilder = builder.callActivity(id).name(name);
        CallActivity task = taskBuilder.getElement();
        configureTaskMetadata(task, namespace);

        String calledElement = findInput(CALLED_ELEMENT).map(ElementNodeInput::getValue).orElse("");
        task.setCalledElement(calledElement);

        BpmnModelInstance modelInstance = (BpmnModelInstance) task.getModelInstance();

        List<ElementNodeInput> inputMappings = findAllInputs(INPUT_MAPPING);
        List<ElementNodeInput> outputMappings = findAllInputs(OUTPUT_MAPPING);

        if (!inputMappings.isEmpty() || !outputMappings.isEmpty()) {
            ExtensionElements extensionElements = getExtensionElements(task);

            for (ElementNodeInput mapping : inputMappings) {
                if (!mapping.hasProperties()) continue;
                CamundaIn camundaIn = modelInstance.newInstance(CamundaIn.class);
                String source = mapping.findPropertyValueOrDefault(SOURCE, null);
                String sourceExpression = mapping.findPropertyValueOrDefault(SOURCE_EXPRESSION, null);
                String target = mapping.findPropertyValueOrDefault(TARGET, null);
                if (source != null && !source.isEmpty()) camundaIn.setCamundaSource(source);
                if (sourceExpression != null && !sourceExpression.isEmpty()) camundaIn.setCamundaSourceExpression(sourceExpression);
                if (target != null && !target.isEmpty()) camundaIn.setCamundaTarget(target);
                extensionElements.addChildElement(camundaIn);
            }

            for (ElementNodeInput mapping : outputMappings) {
                if (!mapping.hasProperties()) continue;
                CamundaOut camundaOut = modelInstance.newInstance(CamundaOut.class);
                String source = mapping.findPropertyValueOrDefault(SOURCE, null);
                String sourceExpression = mapping.findPropertyValueOrDefault(SOURCE_EXPRESSION, null);
                String target = mapping.findPropertyValueOrDefault(TARGET, null);
                if (source != null && !source.isEmpty()) camundaOut.setCamundaSource(source);
                if (sourceExpression != null && !sourceExpression.isEmpty()) camundaOut.setCamundaSourceExpression(sourceExpression);
                if (target != null && !target.isEmpty()) camundaOut.setCamundaTarget(target);
                extensionElements.addChildElement(camundaOut);
            }
        }

        return taskBuilder.done();
    }

    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRenderValues(DomElement dom, FlowNode flowNode, String namespace,
            BpmnComponent.InputVariable iv) {
        if (INPUT_MAPPING.equals(iv.getName()) && flowNode instanceof CallActivity) {
            return reverseRenderMappingElements(dom, flowNode, namespace, iv, CAMUNDA_IN_ELEMENT);
        }
        if (OUTPUT_MAPPING.equals(iv.getName()) && flowNode instanceof CallActivity) {
            return reverseRenderMappingElements(dom, flowNode, namespace, iv, CAMUNDA_OUT_ELEMENT);
        }
        return super.reverseRenderValues(dom, flowNode, namespace, iv);
    }

    private List<ElementNodeInput> reverseRenderMappingElements(DomElement dom, FlowNode flowNode, String namespace,
            BpmnComponent.InputVariable iv, String elementLocalName) {
        List<ElementNodeInput> result = new ArrayList<>();
        List<DomElement> matchingElements = findChildElements(dom, elementLocalName);

        for (DomElement childElement : matchingElements) {
            ElementNodeInput entry = new ElementNodeInput();
            entry.setName(iv.getName());

            List<ElementNodeInput> subProperties = new ArrayList<>();
            if (iv.getProperties() != null) {
                for (BpmnComponent.InputVariable propDef : iv.getProperties()) {
                    subProperties.addAll(reverseRenderLeafValues(childElement, flowNode, namespace, propDef));
                }
            }
            if (!subProperties.isEmpty()) {
                entry.setProperties(subProperties);
                result.add(entry);
            }
        }
        return result;
    }
}
