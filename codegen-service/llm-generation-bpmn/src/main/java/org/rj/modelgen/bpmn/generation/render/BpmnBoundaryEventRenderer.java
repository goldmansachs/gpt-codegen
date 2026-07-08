package org.rj.modelgen.bpmn.generation.render;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractActivityBuilder;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.common.BpmnDefinitionElementResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class BpmnBoundaryEventRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(BpmnBoundaryEventRenderer.class);

    private final BasicBpmnModelGenerator generator;

    BpmnBoundaryEventRenderer(BasicBpmnModelGenerator generator) {
        this.generator = generator;
    }

    void render(List<ElementNode> nodes, Map<String, ElementNode> nodesById, BpmnComponentLibrary componentLibrary, BpmnModelInstance modelInstance, String scope) {
        int seqId = 0;
        final String prefix = "seq-be-" + (scope == null || scope.isEmpty() ? "" : scope + "-");

        for (var node : nodes) {
            if (node.getEvents() == null || node.getEvents().isEmpty()) continue;

            ModelElementInstance parentInstance = modelInstance.getModelElementById(node.getId());
            if (!(parentInstance instanceof Activity activity)) {
                LOG.warn("Node '{}' has boundary events but is not an Activity in the model, skipping", node.getId());
                continue;
            }

            for (var event : node.getEvents()) {
                var activityBuilder = (AbstractActivityBuilder) activity.builder();
                var beBuilder = activityBuilder.boundaryEvent(event.getId());

                BoundaryEvent boundaryEventElement = beBuilder.getElement();
                if (!event.isCancelActivity()) {
                    boundaryEventElement.setCancelActivity(false);
                }

                event.applyEventDefinition(boundaryEventElement, modelInstance,
                        errorCode -> BpmnDefinitionElementResolver.resolveOrCreateError(modelInstance, errorCode));

                if (event.getConnectedTo() == null || event.getConnectedTo().isEmpty()) continue;

                final var seedNodes = new LinkedList<String>();

                for (var connection : event.getConnectedTo()) {
                    String targetId = connection.getTargetNode();
                    var targetNode = nodesById.get(targetId);
                    if (targetNode == null) {
                        LOG.warn("Boundary event '{}' target '{}' not found in nodes", event.getId(), targetId);
                        continue;
                    }

                    String connId = prefix + (seqId++);
                    ModelElementInstance existingTarget = modelInstance.getModelElementById(targetId);

                    if (existingTarget == null) {
                        var elementDef = componentLibrary.getComponentByName(targetNode.getElementType());
                        if (elementDef.isEmpty()) continue;

                        var outbound = generator.addOutboundConnection(beBuilder, node, connection, connId);
                        generator.renderNewNode(outbound, targetNode, elementDef.get());
                        seedNodes.add(targetNode.getId());
                    } else {
                        beBuilder.sequenceFlowId(connId).connectTo(targetId);
                        SequenceFlow sf = modelInstance.getModelElementById(connId);
                        if (sf != null && connection.getDescription() != null) {
                            sf.setAttributeValueNs(generator.getNamespaceUri(), "connectionDescription", connection.getDescription());
                        }
                    }
                }

                // Traverse downstream from newly-created boundary event targets
                for (String seedId : seedNodes) {
                    generator.traverseAndConnect(seedId, nodesById, componentLibrary, modelInstance, prefix + (seqId++) + "-");
                }
            }
        }
    }
}

