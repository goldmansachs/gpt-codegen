package org.rj.modelgen.bpmn.intrep.model;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.rendering.boundaryevents.*;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.camunda.bpm.model.bpmn.impl.BpmnModelConstants.ACTIVITI_NS;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.Namespaces.DEFAULT_NAMESPACE_URI;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.*;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.MultiInstanceConstants.*;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.SUBPROCESS_CALL_NODE;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.SUBPROCESS_DESCRIPTION;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.SUBPROCESS_ID;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.SUBPROCESS_NAME;
import static org.rj.modelgen.bpmn.intrep.model.common.ElementNodeSharedUtils.extractAttributeValue;

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

        List<FlowNode> flowNodes = getFlowNodesInDocumentOrder();

        // Separate boundary events, subprocess elements, and regular flow nodes
        List<BoundaryEvent> boundaryEvents = new ArrayList<>();
        List<SubProcess> subProcesses = new ArrayList<>();
        List<FlowNode> regularNodes = new ArrayList<>();
        for (FlowNode flowNode : flowNodes) {
            if (flowNode instanceof BoundaryEvent boundaryEvent) {
                boundaryEvents.add(boundaryEvent);
            } else if (flowNode instanceof SubProcess subProcess) {
                subProcesses.add(subProcess);
            } else {
                regularNodes.add(flowNode);
            }
        }

        for (FlowNode flowNode : regularNodes) {
            intermediateModel.addNode(ElementNode.fromFlowNode(flowNode, modelAssets, namespace, componentLibrary, globalVariableLibrary));
        }

        for (SubProcess subProcess : subProcesses) {
            boolean isEventSubProcess = subProcess.triggeredByEvent();

            // Embedded subprocesses get an inline call node in the main model (with external connections)
            // Event subprocesses do NOT get a call node as they are only represented as sub-models
            if (!isEventSubProcess) {
                intermediateModel.addNode(buildCallNodeFromSubProcess(subProcess));
            }

            // Build and attach the sub-model containing the subprocess internal flow
            BpmnIntermediateModel subModel = buildSubModel(subProcess);
            if (subModel != null) {
                intermediateModel.getSubModels().add(subModel);
            }
        }

        // Re-attach boundary events to their parent activity nodes (including subprocess nodes)
        attachBoundaryEvents(intermediateModel, boundaryEvents);

        return intermediateModel;
    }

    private ElementNode buildCallNodeFromSubProcess(SubProcess subProcess) {
        final DomElement spDom = subProcess.getDomElement();

        final String storedSubProcessId = extractAttributeValue(spDom, namespace, SUBPROCESS_ID);
        final String subProcessId = storedSubProcessId != null ? storedSubProcessId : subProcess.getId();

        final String storedSubProcessName = extractAttributeValue(spDom, namespace, "workItemName");
        final String subProcessName = storedSubProcessName != null ? storedSubProcessName : resolveSubProcessName(subProcess);

        final String description = extractAttributeValue(spDom, namespace, "nodeDescription");

        final ElementNode callNode = new ElementNode();
        callNode.setElementType(SUBPROCESS_CALL_NODE);
        callNode.setId(subProcess.getId());
        callNode.setName(subProcess.getName() != null && !subProcess.getName().isBlank()
                ? subProcess.getName() : subProcess.getId());
        callNode.setDescription(description);

        if (subProcess.getOutgoing() != null && !subProcess.getOutgoing().isEmpty()) {
            callNode.setConnectedTo(subProcess.getOutgoing().stream()
                    .map(sf -> new ElementConnection(sf.getTarget().getId(), sf.getName(), sf.getId()))
                    .collect(Collectors.toList()));
        }

        final List<ElementNodeInput> inputs = new ArrayList<>();
        addCallNodeInput(inputs, SUBPROCESS_ID, subProcessId, true);
        if (subProcessName != null && !subProcessName.isBlank()) {
            addCallNodeInput(inputs, SUBPROCESS_NAME, subProcessName, false);
        }
        callNode.setInputs(inputs);

        return callNode;
    }

    private static void addCallNodeInput(List<ElementNodeInput> inputs, String name, String value, boolean isProvided) {
        if (value == null || value.isBlank()) return;
        final ElementNodeInput input = new ElementNodeInput();
        input.setName(name);
        input.setValue(value);
        input.setVariableSource("CONSTANT");
        input.setIsProvided(isProvided);
        inputs.add(input);
    }

    private void attachBoundaryEvents(BpmnIntermediateModel intermediateModel, List<BoundaryEvent> boundaryEvents) {
        if (boundaryEvents.isEmpty()) return;

        Map<String, ElementNode> nodeIndex = intermediateModel.getNodes().stream()
                .filter(n -> n.getId() != null)
                .collect(Collectors.toMap(ElementNode::getId, n -> n, (a, b) -> a));

        for (BoundaryEvent boundaryEvent : boundaryEvents) {
            Activity attachedTo = boundaryEvent.getAttachedTo();
            if (attachedTo == null) {
                LOG.warn("BoundaryEvent '{}' has no attached activity, skipping", boundaryEvent.getId());
                continue;
            }

            ElementNode parentNode = nodeIndex.get(attachedTo.getId());
            if (parentNode == null) {
                LOG.warn("BoundaryEvent '{}' attached to '{}' but parent not found in intermediate model, skipping", boundaryEvent.getId(), attachedTo.getId());
                continue;
            }

            BoundaryEventAttachment attachment = buildBoundaryEventAttachment(boundaryEvent);
            if (parentNode.getEvents() == null) {
                parentNode.setEvents(new ArrayList<>());
            }
            parentNode.getEvents().add(attachment);
        }
    }

    private BpmnIntermediateModel buildSubModel(SubProcess subProcess) {
        List<FlowNode> internalNodes = getSubProcessInternalFlowNodes(subProcess);
        if (internalNodes.isEmpty()) {
            LOG.debug("SubProcess '{}' has no internal flow nodes, skipping sub-model creation", subProcess.getId());
            return null;
        }

        BpmnIntermediateModel subModel = new BpmnIntermediateModel();

        // Populate SubProcessConfig
        SubProcessConfig config = new SubProcessConfig();
        // Resolve the original subProcessId from the namespace attribute stored by SubProcessNode.configure() during forward rendering (e.g. "SP1").
        // This ensures subProcessConfig.subProcessId matches the call node's subProcessId input for inline subprocess matching
        DomElement spDom = subProcess.getDomElement();
        String storedSubProcessId = extractAttributeValue(spDom, namespace, SUBPROCESS_ID);
        config.setSubProcessId(storedSubProcessId != null ? storedSubProcessId : subProcess.getId());
        config.setSubProcessName(resolveSubProcessName(subProcess));
        config.setTriggeredByEvent(subProcess.triggeredByEvent());

        String description = extractAttributeValue(spDom, namespace, SUBPROCESS_DESCRIPTION);
        if (description != null) {
            config.setSubProcessDescription(description);
        }

        // Extract multi-instance loop characteristics from the SubProcess element
        LoopCharacteristics loop = subProcess.getLoopCharacteristics();
        if (loop instanceof MultiInstanceLoopCharacteristics miLoop) {
            DomElement miDom = miLoop.getDomElement();
            IterationConfig iterConfig = new IterationConfig();
            iterConfig.setList(extractAttributeValue(miDom, ACTIVITI_NS, MULTI_INSTANCE_COLLECTION));
            iterConfig.setItemInList(extractAttributeValue(miDom, ACTIVITI_NS, MULTI_INSTANCE_ELEMENT_VARIABLE));
            iterConfig.setItemLabel(extractAttributeValue(miDom, namespace, MULTI_INSTANCE_DISPLAY_LABEL));

            CompletionCondition cc = miLoop.getCompletionCondition();
            if (cc != null && cc.getTextContent() != null && !cc.getTextContent().isBlank()) {
                iterConfig.setCompletionCondition(cc.getTextContent());
            }

            if (iterConfig.isConfigured()) {
                config.setIterationConfig(iterConfig);
            }
        }

        subModel.setSubProcessConfig(config);

        // Separate boundary events from regular internal flow nodes
        List<BoundaryEvent> internalBoundaryEvents = new ArrayList<>();
        List<FlowNode> regularInternalNodes = new ArrayList<>();
        for (FlowNode node : internalNodes) {
            if (node instanceof BoundaryEvent be) {
                internalBoundaryEvents.add(be);
            } else {
                regularInternalNodes.add(node);
            }
        }

        // Process internal flow nodes into the sub-model
        for (FlowNode flowNode : regularInternalNodes) {
            subModel.addNode(ElementNode.fromFlowNode(flowNode, modelAssets, namespace, componentLibrary, globalVariableLibrary));
        }

        // Attach boundary events within the subprocess to their parent nodes
        attachBoundaryEvents(subModel, internalBoundaryEvents);

        return subModel;
    }

    private String resolveSubProcessName(SubProcess subProcess) {
        DomElement dom = subProcess.getDomElement();
        // Check for custom namespace attribute first (written by configureTaskMetadata)
        String customName = extractAttributeValue(dom, namespace, "name");
        if (customName != null) return customName;
        // Fall back to the standard BPMN name attribute
        if (subProcess.getName() != null && !subProcess.getName().isBlank()) {
            return subProcess.getName();
        }
        return subProcess.getId();
    }

    private List<FlowNode> getSubProcessInternalFlowNodes(SubProcess subProcess) {
        List<FlowNode> internalNodes = new ArrayList<>();
        DomElement spDom = subProcess.getDomElement();

        for (DomElement childDom : spDom.getChildElements()) {
            String childId = childDom.getAttribute("id");
            if (childId == null) continue;

            ModelElementInstance element = inputModel.getModelElementById(childId);
            if (element instanceof FlowNode flowNode) {
                internalNodes.add(flowNode);
            }
        }

        return internalNodes;
    }

    private BoundaryEventAttachment buildBoundaryEventAttachment(BoundaryEvent boundaryEvent) {
        BoundaryEventAttachment attachment = new BoundaryEventAttachment();
        attachment.setId(boundaryEvent.getId());
        attachment.setName(boundaryEvent.getName());
        attachment.setCancelActivity(boundaryEvent.cancelActivity());

        attachment.setEventType(resolveSpecificBoundaryEventType(boundaryEvent));

        // Build connectedTo from outgoing sequence flows
        if (boundaryEvent.getOutgoing() != null && !boundaryEvent.getOutgoing().isEmpty()) {
            List<ElementConnection> connections = boundaryEvent.getOutgoing().stream()
                    .map(sf -> new ElementConnection(sf.getTarget().getId(), sf.getName(), sf.getId()))
                    .toList();
            attachment.setConnectedTo(connections);
        }

        // return boundary event attachements
        return getBoundaryAttachmentInputs(attachment, boundaryEvent);
    }

    // traversing model in order of components instead of groupBy for sake of IR model comparison
    private List<FlowNode> getFlowNodesInDocumentOrder() {
        List<FlowNode> orderedNodes = new ArrayList<>();

        inputModel.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class).forEach(process ->
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


    private String resolveSpecificBoundaryEventType(BoundaryEvent boundaryEvent) {
        Collection<EventDefinition> eventDefinitions = boundaryEvent.getEventDefinitions();
        if (eventDefinitions != null) {
            for (EventDefinition ed : eventDefinitions) {
                var type = BoundaryEventType.fromEventDefinition(ed);
                if (type.isPresent()) return type.get().getTypeConstant();
            }
        }
        LOG.warn("Could not resolve specific boundary event type for '{}', defaulting to generic", boundaryEvent.getId());
        return BOUNDARY_EVENT;
    }

    private BoundaryEventAttachment getBoundaryAttachmentInputs(BoundaryEventAttachment attachment, BoundaryEvent boundaryEvent) {
        List<ElementNodeInput> inputs = new ArrayList<>();
        BoundaryEventType.fromTypeConstant(attachment.getEventType())
                .ifPresentOrElse(
                        type -> type.extractInputs(boundaryEvent, inputs),
                        () -> LOG.warn("No input extractor for boundary event type '{}'", attachment.getEventType()));
        attachment.setInputs(inputs);
        return attachment;
    }
   }