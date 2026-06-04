package org.rj.modelgen.bpmn.intrep.model.rendering;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.PROCESS_CONFIG;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.ProcessConfigConstants.*;
import static org.rj.modelgen.bpmn.intrep.model.ElementNodeInput.createInputFromAttribute;
import static org.rj.modelgen.bpmn.intrep.model.common.ElementNodeSharedUtils.*;
import static org.rj.modelgen.bpmn.models.generation.validation.BpmnScriptUtils.applyIsProvidedToAllInputs;

public class ProcessConfigNode extends ElementNode {

    public ProcessConfigNode() {
        super();
    }

    public ProcessConfigNode(String id, String name) {
        super(id, name, PROCESS_CONFIG);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        // Process configuration is not rendered as a bpmn node so this is a no-op in the flow.
        return builder.done();
    }

    public void configure(BpmnModelInstance modelInstance, BpmnComponent elementDefinition, String namespace) {
        Process process = modelInstance.getModelElementsByType(Process.class).iterator().next();

        String id = this.findInput(PROCESS_ID).map(ElementNodeInput::getValue).orElse(ATTR_NOT_CONFIGURED);
        String name = this.findInput(PROCESS_NAME).map(ElementNodeInput::getValue).orElse(ATTR_NOT_CONFIGURED);
        process.setId(id);
        process.setName(name);
        configureTaskMetadata(process, namespace);

        if (inputs != null) {
            this.inputs.forEach(input -> {
                if (ATTRIBUTES.contains(input.getName())) {
                    String attrName = getAttrName(input, elementDefinition);
                    process.setAttributeValueNs(namespace, attrName, input.getValue());
                }
            });
        }
    }

    @Override
    public void reverseRenderModel(BpmnModelInstance model, BpmnModelAssets modelAssets, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        Process process = model.getModelElementsByType(Process.class).iterator().next();
        extractNodeMetadata(process, namespace);
        this.connectedTo = List.of();
        this.elementType = PROCESS_CONFIG;

        List<ElementNodeInput> nodeInputs = reverseRender(model, namespace, componentLibrary, globalVariableLibrary);
        BpmnComponent elementDefinition = componentLibrary.getComponentByName(elementType)
                .orElseThrow(() -> new IllegalStateException("No component definition found for element type: " + elementType));

        applyFormatValuesToAllInputs(nodeInputs, componentLibrary, globalVariableLibrary);
        applyIsProvidedToAllInputs(id, nodeInputs, modelAssets, elementDefinition, getUniqueElementIdName());
        setInputs(nodeInputs);
    }

    protected List<ElementNodeInput> reverseRender(BpmnModelInstance model, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        Process process = model.getModelElementsByType(Process.class).iterator().next();
        DomElement processDom = process.getDomElement();
        List<ElementNodeInput> nodeInputs = new ArrayList<>();

        BpmnComponent elementDefinition = componentLibrary.getComponentByName(elementType)
                .orElseThrow(() -> new IllegalStateException("No component definition found for element type: " + elementType));

        for (BpmnComponent.InputVariable iv : elementDefinition.getRequiredInputs()) {
            String inputName = iv.getName();
            String lookupName = getLookupName(iv);
            String attrValue = extractAttributeValue(processDom, namespace, lookupName);

            if (attrValue != null) {
                nodeInputs.add(createInputFromAttribute(inputName, attrValue, true));
            }
        }

        return nodeInputs;
    }

}
