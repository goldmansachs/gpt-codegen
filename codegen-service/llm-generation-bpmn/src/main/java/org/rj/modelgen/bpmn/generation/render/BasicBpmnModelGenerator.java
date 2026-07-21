package org.rj.modelgen.bpmn.generation.render;

import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.*;
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.impl.instance.ModelElementInstanceImpl;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.generation.BpmnConstants;
import org.rj.modelgen.bpmn.intrep.model.*;
import org.rj.modelgen.bpmn.intrep.model.rendering.*;
import org.rj.modelgen.bpmn.intrep.model.rendering.gateways.*;
import org.rj.modelgen.llm.util.Result;
import org.rj.modelgen.llm.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.Namespaces.DEFAULT_NAMESPACE;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.Namespaces.DEFAULT_NAMESPACE_URI;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.PROCESS_CONFIG;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.isStartEventType;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.ProcessConfigConstants.WORKFLOW_ACTION_DETAILS;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.SUBPROCESS;

public class BasicBpmnModelGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(BasicBpmnModelGenerator.class);
    private static final double DIAGRAM_MULTIPLIER = 1.3;

    private final BpmnBoundaryEventRenderer boundaryEventRenderer;
    private final BpmnMultiInstanceRenderer multiInstanceRenderer;
    private final BpmnSubProcessRenderer subProcessRenderer;

    public BasicBpmnModelGenerator() {
        this.boundaryEventRenderer = new BpmnBoundaryEventRenderer(this);
        this.multiInstanceRenderer = new BpmnMultiInstanceRenderer();
        this.subProcessRenderer = new BpmnSubProcessRenderer(this, boundaryEventRenderer, multiInstanceRenderer);
    }

    protected String getNamespaceUri() {
        return DEFAULT_NAMESPACE_URI;
    }

    protected String getNamespacePrefix() {
        return DEFAULT_NAMESPACE;
    }

    public Result<BpmnModelInstance, String> generateModel(BpmnIntermediateModel intermediateModel) {
        return generateModel(intermediateModel, BpmnComponentLibrary.defaultLibrary(), null);
    }

    public Result<BpmnModelInstance, String> generateModel(BpmnIntermediateModel intermediateModel, BpmnComponentLibrary componentLibrary) {
        return generateModel(intermediateModel, componentLibrary, null);
    }

    public Result<BpmnModelInstance, String> generateModel(BpmnIntermediateModel intermediateModel, BpmnComponentLibrary componentLibrary, BpmnOriginalCanvas canvas) {
        if (intermediateModel == null) return Result.Err("Cannot generate model without valid intermediate model");

        deduplicateIds(intermediateModel);

        LOG.info("Generating BPMN model data for graph: {}", Util.serializeOrThrow(intermediateModel));

        final var nodesById = buildNodeIndex(intermediateModel.getNodes());

        final var startNode = findStartNode(nodesById.values()).orElse(null);
        if (startNode == null) return Result.Err("No start event in node data");

        final var processBuilder = Bpmn.createExecutableProcess("process");
        final var modelInstance = processBuilder
                .startEvent(startNode.getId())
                .name(startNode.getName())
                .done();

        final var definitions = modelInstance.getDefinitions();
        registerNamespace("bpmn", BpmnModelConstants.BPMN20_NS, definitions);
        registerNamespace("camunda", BpmnModelConstants.CAMUNDA_NS, definitions);
        registerNamespace("di", BpmnModelConstants.DI_NS, definitions);
        registerNamespace("dc", BpmnModelConstants.DC_NS, definitions);
        registerNamespace("bpmndi", BpmnModelConstants.BPMNDI_NS, definitions);
        registerAdditionalNamespaces(definitions);

        // Configure start event metadata via renderElement for consistent namespace handling
        final StartEvent startEventInstance = modelInstance.getModelElementById(startNode.getId());
        if (startEventInstance != null) {
            componentLibrary.getComponentByName(BpmnConstants.NodeTypes.START_EVENT)
                    .ifPresent(def -> renderElement(startEventInstance.builder(), startNode, def));
        }

        componentLibrary.getComponentByName(BpmnConstants.NodeTypes.PROCESS_CONFIG).ifPresent(configDefinition ->
                intermediateModel.getNodes().stream()
                        .filter(node -> BpmnConstants.NodeTypes.PROCESS_CONFIG.equalsIgnoreCase(node.getElementType()))
                        .findFirst()
                        .ifPresent(processConfig -> setProcessConfiguration(processConfig, configDefinition, modelInstance)));

        // Connect nodes
        traverseAndConnect(startNode.getId(), nodesById, componentLibrary, modelInstance, "seq-");

        // Render inline boundary events attached to nodes
        boundaryEventRenderer.render(intermediateModel.getNodes(), nodesById, componentLibrary, modelInstance, "");

        // Generate subprocess sub-models
        subProcessRenderer.generateSubModels(intermediateModel, componentLibrary, modelInstance, processBuilder);

        // Render workflow actions as extension elements on the Process element
        renderWorkflowActions(modelInstance, getNamespaceUri(), intermediateModel.getNodes());

        // Preserve layout (copilot) or create layout (initial generation / no canvas)
        new BpmnDiagramRestorer().applyIncrementalLayout(modelInstance, canvas, DIAGRAM_MULTIPLIER);

        return Result.Ok(modelInstance);
    }

    // Override in subclasses to add additional namespaces as needed
    protected void registerAdditionalNamespaces(Definitions definitions) {
        registerNamespace(getNamespacePrefix(), getNamespaceUri(), definitions);
    }

    // Override in subclasses to provide custom namespace on process configuration
    protected void setProcessConfiguration(ElementNode processConfig, BpmnComponent configDefinition, BpmnModelInstance builder) {
        ((ProcessConfigNode) processConfig).configure(builder, configDefinition, getNamespaceUri());
    }


    public void traverseAndConnect(String startNodeId, Map<String, ElementNode> nodesById, BpmnComponentLibrary componentLibrary, BpmnModelInstance modelInstance, String sequencePrefix) {
        final var queue = new LinkedList<String>();
        queue.add(startNodeId);
        int[] sequenceId = {0};

        final Set<String> usedSequenceIds = collectPreservedSequenceIds(nodesById.values());

        while (!queue.isEmpty()) {
            final var nodeId = queue.removeFirst();
            final var node = nodesById.get(nodeId);
            if (node == null) continue;

            final ModelElementInstance nodeInstance = modelInstance.getModelElementById(nodeId);
            if (nodeInstance == null || !(nodeInstance instanceof FlowNode flowNode)) continue;

            final var targets = Optional.ofNullable(node.getConnectedTo()).orElseGet(List::of);
            for (var target : targets) {
                var targetElement = nodesById.get(target.getTargetNode());
                if (targetElement == null) {
                    LOG.warn("Target element '{}' not found (prefix: '{}')", target.getTargetNode(), sequencePrefix);
                    continue;
                }

                final var elementDefinition = componentLibrary.getComponentByName(targetElement.getElementType());
                if (elementDefinition.isEmpty()) continue;

                final String preservedId = target.getSequenceFlowId();
                final var connectionId = (preservedId != null && !preservedId.isBlank()) ? preservedId : nextUniqueSequenceId(sequencePrefix, sequenceId, usedSequenceIds);
                final var outboundConnection = addOutboundConnection(flowNode.builder(), node, target, connectionId);

                final ModelElementInstance existingTarget = modelInstance.getModelElementById(target.getTargetNode());
                if (existingTarget == null) {
                    renderNewNode(outboundConnection, targetElement, elementDefinition.get());
                    queue.add(targetElement.getId());
                } else {
                    outboundConnection.connectTo(target.getTargetNode());
                }
            }
        }
    }

    static Set<String> collectPreservedSequenceIds(Collection<ElementNode> nodes) {
        final Set<String> ids = new HashSet<>();
        for (var node : nodes) {
            for (var conn : Optional.ofNullable(node.getConnectedTo()).orElseGet(List::of)) {
                String id = conn.getSequenceFlowId();
                if (id != null && !id.isBlank()) ids.add(id);
            }
        }
        return ids;
    }

    static String nextUniqueSequenceId(String prefix, int[] counter, Set<String> usedIds) {
        String seqId;
        do {
            seqId = prefix + (counter[0]++);
        } while (!usedIds.add(seqId));
        return seqId;
    }

    protected void registerNamespace(String namespacePrefix, String namespaceUri, Definitions definitions) {
        if (definitions == null) return;
        try {
            var dom = ((ModelElementInstanceImpl) definitions).getDomElement();
            String existing = dom.getAttribute("xmlns:" + namespacePrefix);
            if (existing == null || !existing.equals(namespaceUri)) {
                dom.registerNamespace(namespacePrefix, namespaceUri);
            }
        } catch (ClassCastException e) {
            LOG.warn("Failed to register {} namespace (unexpected type): {}", namespaceUri, e.getMessage());
        }
    }

    // Renders a new node into the model
    protected <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode>
    void renderNewNode(AbstractFlowNodeBuilder<B, E> builder, ElementNode element, BpmnComponent elementDefinition) {
        if (element == null) throw new RuntimeException("Cannot generate definition for null BPMN element");
        renderElement(builder, element, elementDefinition);
    }

    protected <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode>
    BpmnModelInstance renderElement(AbstractFlowNodeBuilder<B, E> builder, ElementNode element, BpmnComponent elementDefinition) {
        BpmnModelInstance modelInstance = element.render(builder, elementDefinition, getNamespaceUri());
        multiInstanceRenderer.applyIfRepeatable(element, modelInstance, getNamespaceUri());
        return modelInstance;
    }

    protected <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode>
    AbstractFlowNodeBuilder<B, E> addOutboundConnection(AbstractFlowNodeBuilder<B, E> builder, ElementNode sourceElement,
                                                        ElementConnection connection, String id) {
        if (builder == null || sourceElement == null || connection == null || StringUtils.isBlank(id))
            throw new RuntimeException("Cannot make connection with invalid null data");

        final var outboundConnection = builder.sequenceFlowId(id);

        final SequenceFlow sequenceFlow = builder.done().getModelElementById(id);
        if (sequenceFlow != null && connection.getDescription() != null) {
            sequenceFlow.setName(connection.getDescription());
        }

        if (sourceElement instanceof ConditionalGateway conditionalGateway) {
            conditionalGateway.renderConditionalConnections(builder, connection, id, outboundConnection);
        }

        return outboundConnection;
    }

    private static void renderWorkflowActions(BpmnModelInstance modelInstance, String namespace, List<ElementNode> nodes) {
        ElementNode processConfig = nodes.stream()
                .filter(node -> PROCESS_CONFIG.equalsIgnoreCase(node.getElementType()))
                .findFirst()
                .orElse(null);
        if (processConfig == null) return;

        List<ElementNodeInput> actionInputs = processConfig.findAllInputs(WORKFLOW_ACTION_DETAILS);
        if (actionInputs.isEmpty()) return;

        Process process = modelInstance.getModelElementsByType(Process.class).iterator().next();
        ExtensionElements extensionElements = process.getExtensionElements();
        if (extensionElements == null) {
            extensionElements = modelInstance.newInstance(ExtensionElements.class);
            process.setExtensionElements(extensionElements);
        }

        for (ElementNodeInput actionInput : actionInputs) {
            if (!actionInput.hasProperties()) continue;
            ModelElementInstance actionElement = extensionElements.addExtensionElement(namespace, WORKFLOW_ACTION_DETAILS);
            DomElement dom = actionElement.getDomElement();
            for (ElementNodeInput prop : actionInput.getProperties()) {
                if (prop.getName() != null && prop.getValue() != null) {
                    dom.setAttribute(prop.getName(), prop.getValue());
                }
            }
        }
    }

    protected void deduplicateIds(BpmnIntermediateModel intermediateModel) {
        if (intermediateModel == null) return;

        final Set<String> globalIds = new HashSet<>();

        deduplicateNodesInScope(intermediateModel.getNodes(), globalIds);

        if (!intermediateModel.hasSubModels()) return;

        final Set<String> seenSubProcessIds = new HashSet<>();
        final Map<String, String> renamedSubprocesses = new LinkedHashMap<>();

        for (var subModel : intermediateModel.getSubModels()) {
            final var config = subModel.getSubProcessConfig();
            if (config == null) continue;

            String spId = config.getSubProcessId();
            if (spId != null && !seenSubProcessIds.add(spId)) {
                String newSpId = makeUniqueId(spId, globalIds);
                LOG.warn("Duplicate subProcessId '{}' detected, renaming to '{}'", spId, newSpId);
                config.setSubProcessId(newSpId);
                globalIds.add(newSpId);
                updateParentCallNodeSubProcessId(intermediateModel, spId, newSpId);
            } else if (spId != null) {
                globalIds.add(spId);
            } else {
                String subProcessName = config.getSubProcessName();
                String uniqueId = makeUniqueId(subProcessName != null ? subProcessName : SUBPROCESS, globalIds);
                LOG.warn("SubProcessId is null, assigning default id '{}'", uniqueId);
                config.setSubProcessId(uniqueId);
                globalIds.add(uniqueId);
                seenSubProcessIds.add(uniqueId);
                updateParentCallNodeSubProcessId(intermediateModel, subProcessName, uniqueId);
            }

            renamedSubprocesses.putAll(deduplicateNodesInScope(subModel.getNodes(), globalIds));
        }

        if (!renamedSubprocesses.isEmpty()) {
            updateNodeReferences(intermediateModel.getNodes(), renamedSubprocesses);
        }
    }

    private void updateParentCallNodeSubProcessId(BpmnIntermediateModel intermediateModel, String oldValue, String newValue) {
        if (oldValue == null || oldValue.equals(newValue)) return;
        intermediateModel.getNodes().stream()
                .filter(ElementNode::isSubprocessCallNode)
                .filter(n -> oldValue.equals(
                        n.findInput(BpmnConstants.SubProcessConfigConstants.SUBPROCESS_ID)
                                .map(ElementNodeInput::getValue).orElse(null)))
                .forEach(n -> n.findInput(BpmnConstants.SubProcessConfigConstants.SUBPROCESS_ID)
                        .ifPresent(input -> input.setValue(newValue)));
    }

    private Map<String, String> deduplicateNodesInScope(List<ElementNode> nodes, Set<String> globalIds) {
        final Map<String, String> idMapping = new LinkedHashMap<>();
        for (var node : nodes) {
            String nodeId = node.getId();
            if (nodeId != null && !globalIds.add(nodeId)) {
                String newId = makeUniqueId(nodeId, globalIds);
                LOG.warn("Duplicate node ID '{}' detected, renaming to '{}'", nodeId, newId);
                idMapping.put(nodeId, newId);
                node.setId(newId);
                globalIds.add(newId);
            }
            if (node.getEvents() != null) {
                for (var event : node.getEvents()) {
                    String eventId = event.getId();
                    if (eventId != null && !globalIds.add(eventId)) {
                        String newEventId = makeUniqueId(eventId, globalIds);
                        LOG.warn("Duplicate boundary event ID '{}' detected, renaming to '{}'", eventId, newEventId);
                        event.setId(newEventId);
                        globalIds.add(newEventId);
                    }
                }
            }
        }
        if (!idMapping.isEmpty()) {
            updateNodeReferences(nodes, idMapping);
        }
        return idMapping;
    }

    private void updateNodeReferences(List<ElementNode> nodes, Map<String, String> idMapping) {
        for (var node : nodes) {
            // Update connectedTo references
            if (node.getConnectedTo() != null) {
                for (var conn : node.getConnectedTo()) {
                    String mapped = idMapping.get(conn.getTargetNode());
                    if (mapped != null) conn.setTargetNode(mapped);
                }
            }
            // Update boundary event connectedTo references
            if (node.getEvents() != null) {
                for (var event : node.getEvents()) {
                    if (event.getConnectedTo() != null) {
                        for (var conn : event.getConnectedTo()) {
                            String mapped = idMapping.get(conn.getTargetNode());
                            if (mapped != null) conn.setTargetNode(mapped);
                        }
                    }
                }
            }
            // Update gateway condition targetNodeId and default references
            if (node.getInputs() != null) {
                for (var input : node.getInputs()) {
                    if (BpmnConstants.GatewayConstants.CONDITIONS.equals(input.getName()) && input.getProperties() != null) {
                        for (var prop : input.getProperties()) {
                            if (BpmnConstants.GatewayConstants.TARGET_NODE_ID.equals(prop.getName())) {
                                String mapped = idMapping.get(prop.getValue());
                                if (mapped != null) prop.setValue(mapped);
                            }
                        }
                    }
                    if (BpmnConstants.GatewayConstants.DEFAULT.equals(input.getName())) {
                        String mapped = idMapping.get(input.getValue());
                        if (mapped != null) input.setValue(mapped);
                    }
                }
            }
        }
    }

    private String makeUniqueId(String baseId, Set<String> existingIds) {
        int counter = 2;
        String newId;
        do {
            newId = baseId + "_" + counter++;
        } while (existingIds.contains(newId));
        return newId;
    }

    public Map<String, ElementNode> buildNodeIndex(List<ElementNode> nodes) {
        return nodes.stream().collect(Collectors.toMap(ElementNode::getId, Function.identity()));
    }


    public Optional<ElementNode> findStartNode(Collection<ElementNode> nodes) {
        return nodes.stream()
                .filter(node -> isStartEventType(node.getElementType()))
                .findFirst();
    }
}
