package org.rj.modelgen.bpmn.intrep.model;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.Namespaces.DEFAULT_NAMESPACE_URI;

public class BpmnReverseRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(BpmnReverseRenderer.class);
    private final BpmnModelInstance inputModel;
    private final BpmnModelAssets modelAssets;
    private final BpmnComponentLibrary componentLibrary;
    private final BpmnGlobalVariableLibrary globalVariableLibrary;
    private String namespace;

    public BpmnReverseRenderer(BpmnModelInstance inputModel, BpmnModelAssets modelAssets, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        this.inputModel = inputModel;
        this.modelAssets = modelAssets;
        this.componentLibrary = componentLibrary;
        this.globalVariableLibrary = globalVariableLibrary;
        this.namespace = DEFAULT_NAMESPACE_URI;
        if(inputModel == null) throw new IllegalArgumentException("Null input model");
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public BpmnIntermediateModel generateBpmnIntermediateModel() {
        LOG.info("Starting generation of BPMN Intermediate Model");
        BpmnIntermediateModel intermediateModel = new BpmnIntermediateModel();

        intermediateModel.addNode(ElementNode.fromProcess(inputModel, modelAssets, namespace, componentLibrary, globalVariableLibrary));

        for (FlowNode flowNode : getFlowNodesInDocumentOrder()) {
            intermediateModel.addNode(ElementNode.fromFlowNode(flowNode, modelAssets, namespace, componentLibrary, globalVariableLibrary));
        }

        return intermediateModel;
    }

    // traversing model in order of components instead of groupBy for sake of IR model comparison
    private List<FlowNode> getFlowNodesInDocumentOrder() {
        List<FlowNode> orderedNodes = new ArrayList<>();

        inputModel.getModelElementsByType(Process.class).forEach(process ->
                process.getDomElement().getChildElements().forEach(childDom -> {
                    ModelElementInstance modelElement = inputModel.getModelElementById(childDom.getAttribute("id"));
                    if (modelElement instanceof FlowNode flowNode) {
                        orderedNodes.add(flowNode);
                    }
                })
        );

        // Fall back
        if (orderedNodes.isEmpty()) {
            orderedNodes.addAll(inputModel.getModelElementsByType(FlowNode.class));
        }

        return orderedNodes;
    }
   }