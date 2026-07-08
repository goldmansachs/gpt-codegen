package org.rj.modelgen.bpmn.generation.render;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnDiagram;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.generation.BpmnConstants;
import org.rj.modelgen.bpmn.intrep.model.*;
import org.rj.modelgen.bpmn.intrep.model.rendering.SubProcessNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BpmnSubProcessRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(BpmnSubProcessRenderer.class);
    private static final double SUBPROCESS_PADDING = 50;

    private final BasicBpmnModelGenerator generator;
    private final BpmnBoundaryEventRenderer boundaryEventRenderer;
    private final BpmnMultiInstanceRenderer multiInstanceConfigurator;

    BpmnSubProcessRenderer(BasicBpmnModelGenerator generator,
                           BpmnBoundaryEventRenderer boundaryEventRenderer,
                           BpmnMultiInstanceRenderer multiInstanceConfigurator) {
        this.generator = generator;
        this.boundaryEventRenderer = boundaryEventRenderer;
        this.multiInstanceConfigurator = multiInstanceConfigurator;
    }

    public void generateSubModels(BpmnIntermediateModel intermediateModel, BpmnComponentLibrary componentLibrary,
                           BpmnModelInstance modelInstance, org.camunda.bpm.model.bpmn.builder.ProcessBuilder processBuilder) {
        if (!intermediateModel.hasSubModels()) return;

        for (var subModel : intermediateModel.getSubModels()) {
            SubProcessNode spNode = resolveSubProcessConfig(subModel);
            if (spNode == null) {
                LOG.debug("Sub-model has no subprocess configuration and no valid nodes, skipping");
                continue;
            }

            String subProcessId = spNode.findInput(BpmnConstants.SubProcessConfigConstants.SUBPROCESS_ID)
                    .map(ElementNodeInput::getValue)
                    .orElse(spNode.getId());

            final var nodesById = generator.buildNodeIndex(subModel.getNodes());
            if (!generator.findStartNode(nodesById.values()).isPresent()) {
                LOG.warn("Sub-model '{}' has no start event, skipping", subProcessId);
                continue;
            }

            boolean isEventSubProcess = spNode.isTriggeredByEvent();
            String inlineNodeId = isEventSubProcess ? null : findInlineSubProcessNodeId(intermediateModel, subProcessId);

            if (isEventSubProcess) {
                generateEventSubProcess(processBuilder, subProcessId, spNode, nodesById, componentLibrary, modelInstance);
            } else {
                if (inlineNodeId != null && !spNode.isRepeatable()) {
                    inheritIterationConfigFromCallNode(intermediateModel, inlineNodeId, spNode);
                }
                String id = (inlineNodeId != null) ? inlineNodeId : subProcessId;
                generateEmbeddedSubProcess(id, spNode, nodesById, componentLibrary, modelInstance);
            }

            // Apply custom namespace attributes and metadata to the subprocess element
            String id = isEventSubProcess ? subProcessId : Optional.ofNullable(inlineNodeId).orElse(subProcessId);
            ModelElementInstance subProcessElement = modelInstance.getModelElementById(id);
            if (subProcessElement instanceof SubProcess subProcess) {
                componentLibrary.getComponentByName(BpmnConstants.SubProcessConfigConstants.SUBPROCESS).ifPresent(def -> spNode.configure(subProcess, def, generator.getNamespaceUri()));

                // Apply multi-instance config from the subprocess config node
                if (spNode.isRepeatable() && subProcess.getLoopCharacteristics() == null) {
                    multiInstanceConfigurator.applyToActivity(spNode.getIterationConfig(), subProcess, modelInstance, generator.getNamespaceUri());
                }
            }
        }
    }

    private SubProcessNode resolveSubProcessConfig(BpmnIntermediateModel subModel) {
        final SubProcessConfig config = subModel.getSubProcessConfig();
        if (config == null || !config.isConfigured()) {
            return null;
        }

        String id = config.getSubProcessId() != null ? config.getSubProcessId() : config.getSubProcessName();
        SubProcessNode spNode = new SubProcessNode(id, config.getSubProcessName());
        List<ElementNodeInput> inputs = new ArrayList<>();
        addConfigInput(inputs, BpmnConstants.SubProcessConfigConstants.SUBPROCESS_ID, id);
        addConfigInput(inputs, BpmnConstants.SubProcessConfigConstants.SUBPROCESS_NAME, config.getSubProcessName());
        if (config.getSubProcessDescription() != null) {
            addConfigInput(inputs, BpmnConstants.SubProcessConfigConstants.SUBPROCESS_DESCRIPTION, config.getSubProcessDescription());
        }
        addConfigInput(inputs, BpmnConstants.SubProcessConfigConstants.TRIGGERED_BY_EVENT, String.valueOf(config.isTriggeredByEvent()));
        spNode.setInputs(inputs);

        if (config.getIterationConfig() != null) {
            spNode.setIterationConfig(config.getIterationConfig());
        }

        return spNode;
    }

    private static void addConfigInput(List<ElementNodeInput> inputs, String name, String value) {
        ElementNodeInput input = new ElementNodeInput();
        input.setName(name);
        input.setValue(value);
        inputs.add(input);
    }

    public void generateEventSubProcess(org.camunda.bpm.model.bpmn.builder.ProcessBuilder processBuilder, String subProcessId,
                                 SubProcessNode spNode, Map<String, ElementNode> nodesById,
                                 BpmnComponentLibrary componentLibrary, BpmnModelInstance modelInstance) {
        var startNode = generator.findStartNode(nodesById.values()).orElse(null);
        if (startNode == null) {
            LOG.warn("Event sub-model '{}' has no start event, skipping", subProcessId);
            return;
        }

        var espBuilder = processBuilder.eventSubProcess(subProcessId)
                .name(spNode.getSubProcessName());

        var startEventBuilder = espBuilder.startEvent(startNode.getId())
                .name(startNode.getName());

        startNode.configureEventSubProcessStart(startEventBuilder, modelInstance);

        StartEvent startEventElement = modelInstance.getModelElementById(startNode.getId());
        if (startEventElement != null) {
            startNode.configureTaskMetadata(startEventElement, generator.getNamespaceUri());
        }

        // Ensure event subprocess shape is expanded and positioned
        ModelElementInstance eventSp = modelInstance.getModelElementById(subProcessId);
        if (eventSp instanceof SubProcess sp) {
            BpmnShape shape = findDiagramShape(modelInstance, sp);
            if (shape != null) {
                shape.setExpanded(true);
                double yOffset = computeNextSubProcessY(modelInstance, sp);
                shape.getBounds().setY(yOffset);
            }
        }

        buildSubProcessInternalFlow(subProcessId, nodesById, componentLibrary, modelInstance);
    }

    public void generateEmbeddedSubProcess(String subProcessId, SubProcessNode spNode,
                                    Map<String, ElementNode> nodesById,
                                    BpmnComponentLibrary componentLibrary, BpmnModelInstance modelInstance) {
        var startNode = generator.findStartNode(nodesById.values()).orElse(null);
        if (startNode == null) {
            LOG.warn("Embedded sub-model '{}' has no start event, skipping", subProcessId);
            return;
        }

        ModelElementInstance existingElement = modelInstance.getModelElementById(subProcessId);
        SubProcess subProcess;
        if (existingElement instanceof SubProcess sp) {
            subProcess = sp;
            BpmnShape existingShape = findDiagramShape(modelInstance, subProcess);
            if (existingShape != null) {
                existingShape.setExpanded(true);
                Bounds bounds = existingShape.getBounds();
                if (bounds.getWidth() < 350) bounds.setWidth(350);
                if (bounds.getHeight() < 200) bounds.setHeight(200);
            }
        } else if (existingElement != null) {
            subProcess = replaceWithSubProcess(subProcessId, existingElement, modelInstance);
        } else {
            Process process = modelInstance.getModelElementsByType(Process.class).iterator().next();
            subProcess = createElement(process, subProcessId, SubProcess.class);
            double yOffset = computeNextSubProcessY(modelInstance, subProcess);
            createDiagramShape(modelInstance, subProcess, 200, yOffset, 350, 200, true);
        }

        if (spNode != null) {
            String spName = spNode.getSubProcessName();
            if (spName != null) subProcess.setName(spName);
        }

        BpmnShape spShape = findDiagramShape(modelInstance, subProcess);
        double startX = (spShape != null) ? spShape.getBounds().getX() + 16 : 216;
        double startY = (spShape != null) ? spShape.getBounds().getY() + 58 : 258;

        StartEvent startEvent = createElement(subProcess, startNode.getId(), StartEvent.class);
        startEvent.setName(startNode.getName());
        createDiagramShape(modelInstance, startEvent, startX, startY, 36, 36, false);

        componentLibrary.getComponentByName(startNode.getElementType()).ifPresent(def -> generator.renderElement(startEvent.builder(), startNode, def));

        buildSubProcessInternalFlow(subProcessId, nodesById, componentLibrary, modelInstance);
    }

    private SubProcess replaceWithSubProcess(String id, ModelElementInstance existingElement, BpmnModelInstance modelInstance) {
        LOG.info("Replacing existing {} element '{}' with SubProcess for embedded subprocess generation",
                existingElement.getClass().getSimpleName(), id);

        // Collect incoming/outgoing sequence flows before removing
        List<SequenceFlow> incoming = new ArrayList<>();
        List<SequenceFlow> outgoing = new ArrayList<>();
        if (existingElement instanceof FlowNode flowNode) {
            incoming.addAll(flowNode.getIncoming());
            outgoing.addAll(flowNode.getOutgoing());
        }

        // Capture original shape position before removing (for inline rendering)
        double origX = 200, origY = 200;
        if (existingElement instanceof BaseElement baseElement) {
            BpmnShape shape = findDiagramShape(modelInstance, baseElement);
            if (shape != null) {
                Bounds bounds = shape.getBounds();
                origX = bounds.getX();
                origY = bounds.getY();
                shape.getParentElement().removeChildElement(shape);
            }
        }

        // Remove the existing element, create a SubProcess under the same parent
        ModelElementInstance parent = existingElement.getParentElement();
        parent.removeChildElement(existingElement);
        SubProcess subProcess = createElement((BpmnModelElementInstance) parent, id, SubProcess.class);

        // Re-attach sequence flows
        for (SequenceFlow sf : incoming) {
            sf.setTarget(subProcess);
        }
        for (SequenceFlow sf : outgoing) {
            sf.setSource(subProcess);
        }

        createDiagramShape(modelInstance, subProcess, origX, origY, 350, 200, true);

        return subProcess;
    }

    private void buildSubProcessInternalFlow(String subProcessId, Map<String, ElementNode> nodesById,
                                             BpmnComponentLibrary componentLibrary, BpmnModelInstance modelInstance) {
        var startNode = generator.findStartNode(nodesById.values()).orElse(null);
        if (startNode == null) return;

        generator.traverseAndConnect(startNode.getId(), nodesById, componentLibrary, modelInstance, "seq-sp-" + subProcessId + "-");

        // Render boundary events on subprocess internal nodes
        boundaryEventRenderer.render(new ArrayList<>(nodesById.values()), nodesById, componentLibrary, modelInstance, subProcessId);
    }


    /**
     * Finds the main process' inline subprocess call node whose {@code subProcessId} input matches the given ID.
     */
    private String findInlineSubProcessNodeId(BpmnIntermediateModel parentModel, String subProcessId) {
        if (subProcessId == null || subProcessId.isBlank()) return null;

        return parentModel.getNodes().stream()
                .filter(node -> BpmnConstants.SubProcessConfigConstants.SUBPROCESS.equals(node.getElementType()))
                .filter(node -> node.getConnectedTo() != null && !node.getConnectedTo().isEmpty())
                .filter(node -> {
                    String nodeSpId = node.findInput(BpmnConstants.SubProcessConfigConstants.SUBPROCESS_ID)
                            .map(ElementNodeInput::getValue)
                            .orElse(null);
                    return subProcessId.equals(nodeSpId);
                })
                .map(ElementNode::getId)
                .findFirst()
                .orElse(null);
    }

    private void inheritIterationConfigFromCallNode(BpmnIntermediateModel parentModel,
                                                    String callNodeId,
                                                    SubProcessNode spNode) {
        parentModel.getNodes().stream()
                .filter(n -> callNodeId.equals(n.getId()))
                .findFirst()
                .filter(ElementNode::isRepeatable)
                .ifPresent(callNode -> {
                    LOG.info("Inheriting iterationConfig from call node '{}' for subprocess '{}'",
                            callNodeId, spNode.getSubProcessName());
                    spNode.setIterationConfig(callNode.getIterationConfig());
                });
    }

    private <T extends BpmnModelElementInstance> T createElement(BpmnModelElementInstance parentElement, String id, Class<T> elementClass) {
        T element = parentElement.getModelInstance().newInstance(elementClass);
        element.setAttributeValue("id", id, true);
        parentElement.addChildElement(element);
        return element;
    }

    private void createDiagramShape(BpmnModelInstance modelInstance, BaseElement element,
                                    double x, double y, double width, double height, boolean isExpanded) {
        BpmnDiagram diagram = modelInstance.getModelElementsByType(BpmnDiagram.class).iterator().next();
        BpmnPlane plane = diagram.getBpmnPlane();

        BpmnShape shape = modelInstance.newInstance(BpmnShape.class);
        shape.setBpmnElement(element);
        if (isExpanded) {
            shape.setExpanded(true);
        }
        Bounds bounds = modelInstance.newInstance(Bounds.class);
        bounds.setX(x);
        bounds.setY(y);
        bounds.setWidth(width);
        bounds.setHeight(height);
        shape.setBounds(bounds);
        plane.addChildElement(shape);
    }

    private BpmnShape findDiagramShape(BpmnModelInstance modelInstance, BaseElement element) {
        for (BpmnShape shape : modelInstance.getModelElementsByType(BpmnShape.class)) {
            if (element.equals(shape.getBpmnElement())) return shape;
        }
        return null;
    }

    private double computeNextSubProcessY(BpmnModelInstance modelInstance, BaseElement excludeElement) {
        double maxBottom = 0;
        for (BpmnShape shape : modelInstance.getModelElementsByType(BpmnShape.class)) {
            BaseElement el = shape.getBpmnElement();
            if (el != null && el.equals(excludeElement)) continue;
            Bounds bounds = shape.getBounds();
            if (bounds != null) {
                maxBottom = Math.max(maxBottom, bounds.getY() + bounds.getHeight());
            }
        }
        return maxBottom + SUBPROCESS_PADDING;
    }
}
