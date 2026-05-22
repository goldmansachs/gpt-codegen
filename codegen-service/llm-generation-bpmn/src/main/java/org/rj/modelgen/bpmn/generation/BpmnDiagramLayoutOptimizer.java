package org.rj.modelgen.bpmn.generation;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;

import java.util.*;

public class BpmnDiagramLayoutOptimizer {

    private record SideAssignment(Side exitSide, Side entrySide) {}

    public enum Side { TOP, RIGHT, BOTTOM, LEFT }

    private static final Map<Side, Side[]> CONNECTING_ORDER = Map.of(
            Side.RIGHT,  new Side[]{Side.RIGHT,  Side.BOTTOM, Side.TOP,    Side.LEFT},
            Side.LEFT,   new Side[]{Side.LEFT,   Side.TOP,    Side.BOTTOM, Side.RIGHT},
            Side.BOTTOM, new Side[]{Side.BOTTOM, Side.RIGHT,  Side.LEFT,   Side.TOP},
            Side.TOP,    new Side[]{Side.TOP,    Side.LEFT,   Side.RIGHT,  Side.BOTTOM}
    );

    public void applySpacingMultiplier(BpmnModelInstance modelInstance, double multiplier) {
        final Map<String, Bounds> boundsById = scaleNodePositions(modelInstance, multiplier);
        final Map<String, Set<Side>> exitSides = new HashMap<>();
        final Map<String, Set<Side>> entrySides = new HashMap<>();

        final List<BpmnEdge> sequenceFlowEdges = new ArrayList<>();
        for (BpmnEdge edge : modelInstance.getModelElementsByType(BpmnEdge.class)) {
            BaseElement el = edge.getBpmnElement();
            if (el instanceof SequenceFlow) {
                sequenceFlowEdges.add(edge);
            } else {
                scaleConnectors(edge, multiplier);
            }
        }

        final Map<BpmnEdge, SideAssignment> assignments = assignSides(sequenceFlowEdges, boundsById, multiplier, exitSides, entrySides);
        rebuildConnectors(modelInstance, assignments, boundsById);
    }

    private double[] getMidpointCoords(Bounds b, Side side) {
        return switch (side) {
            case TOP    -> new double[]{centerX(b), b.getY()};
            case BOTTOM -> new double[]{centerX(b), b.getY() + b.getHeight()};
            case LEFT   -> new double[]{b.getX(), centerY(b)};
            case RIGHT  -> new double[]{b.getX() + b.getWidth(), centerY(b)};
        };
    }

    private static double centerX(Bounds b) { return b.getX() + b.getWidth()  / 2.0; }
    private static double centerY(Bounds b) { return b.getY() + b.getHeight() / 2.0; }


    private Map<String, Bounds> scaleNodePositions(BpmnModelInstance modelInstance, double multiplier) {
        final Map<String, Bounds> boundsById = new HashMap<>();
        for (BpmnShape shape : modelInstance.getModelElementsByType(BpmnShape.class)) {
            Bounds bounds = shape.getBounds();
            if (bounds == null) continue;
            bounds.setX(bounds.getX() * multiplier);
            bounds.setY(bounds.getY() * multiplier);
            BaseElement el = shape.getBpmnElement();
            if (el != null) boundsById.put(el.getId(), bounds);
        }
        return boundsById;
    }

    private void scaleConnectors(BpmnEdge edge, double multiplier) {
        for (Waypoint wp : edge.getWaypoints()) {
            wp.setX(wp.getX() * multiplier);
            wp.setY(wp.getY() * multiplier);
        }
    }

    private Map<BpmnEdge, SideAssignment> assignSides(List<BpmnEdge> sequenceFlowEdges, Map<String, Bounds> boundsById, double multiplier, Map<String, Set<Side>> exitSides, Map<String, Set<Side>> entrySides) {
        final Map<BpmnEdge, SideAssignment> assignments = new LinkedHashMap<>();

        for (BpmnEdge edge : sequenceFlowEdges) {
            SequenceFlow seqFlow = (SequenceFlow) edge.getBpmnElement();
            FlowNode source = seqFlow.getSource();
            FlowNode target = seqFlow.getTarget();
            Bounds srcBounds = source != null ? boundsById.get(source.getId()) : null;
            Bounds tgtBounds = target != null ? boundsById.get(target.getId()) : null;

            if (srcBounds == null || tgtBounds == null || edge.getWaypoints().size() < 2) {
                scaleConnectors(edge, multiplier);
                continue;
            }

            Side exitSide = assignExitSide(srcBounds, tgtBounds, source.getId(), exitSides, entrySides);
            Side entrySide = assignEntrySide(tgtBounds, srcBounds, target.getId(), exitSides, entrySides);
            assignments.put(edge, new SideAssignment(exitSide, entrySide));
        }

        return assignments;
    }

    private void rebuildConnectors(BpmnModelInstance modelInstance, Map<BpmnEdge, SideAssignment> assignments, Map<String, Bounds> boundsById) {
        Set<String> alignedCircularNodes = new HashSet<>();

        for (var entry : assignments.entrySet()) {
            BpmnEdge edge = entry.getKey();
            SideAssignment assignment = entry.getValue();
            SequenceFlow seqFlow = (SequenceFlow) edge.getBpmnElement();

            FlowNode source = seqFlow.getSource();
            FlowNode target = seqFlow.getTarget();
            Bounds srcBounds = boundsById.get(source.getId());
            Bounds tgtBounds = boundsById.get(target.getId());

            boolean srcCircular = isCircularNode(source);
            boolean tgtCircular = isCircularNode(target);

            // Align circular node positions to match connected rectangular node centers,
            // but only if the new position does not overlap any other node
            if (srcCircular && !tgtCircular && alignedCircularNodes.add(source.getId())) {
                double alignedY = centerY(tgtBounds) - srcBounds.getHeight() / 2.0;
                if (!wouldOverlap(source.getId(), srcBounds.getX(), alignedY, srcBounds.getWidth(), srcBounds.getHeight(), boundsById)) {
                    srcBounds.setY(alignedY);
                }
            }
            if (tgtCircular && !srcCircular && alignedCircularNodes.add(target.getId())) {
                double alignedY = centerY(srcBounds) - tgtBounds.getHeight() / 2.0;
                if (!wouldOverlap(target.getId(), tgtBounds.getX(), alignedY, tgtBounds.getWidth(), tgtBounds.getHeight(), boundsById)) {
                    tgtBounds.setY(alignedY);
                }
            }

            double[] sourcePoint = getMidpointCoords(srcBounds, assignment.exitSide);
            double[] targetPoint = getMidpointCoords(tgtBounds, assignment.entrySide);

            replaceConnectors(modelInstance, edge, sourcePoint, targetPoint, assignment.exitSide, assignment.entrySide);
        }
    }

    // Checks whether placing a node at the given position would overlap any other node.
    private boolean wouldOverlap(String nodeId, double x, double y, double width, double height, Map<String, Bounds> boundsById) {
        for (var entry : boundsById.entrySet()) {
            if (entry.getKey().equals(nodeId)) continue;
            Bounds other = entry.getValue();
            if (x < other.getX() + other.getWidth() &&
                x + width > other.getX() &&
                y < other.getY() + other.getHeight() &&
                y + height > other.getY()) {
                return true;
            }
        }
        return false;
    }


    private Side assignExitSide(Bounds nodeBounds, Bounds connectedNodeBounds, String nodeId,
                                Map<String, Set<Side>> exitSides, Map<String, Set<Side>> entrySides) {
        Side preferred = preferredSide(nodeBounds, connectedNodeBounds);
        Set<Side> exits = exitSides.computeIfAbsent(nodeId, k -> EnumSet.noneOf(Side.class));
        Set<Side> entries = entrySides.computeIfAbsent(nodeId, k -> EnumSet.noneOf(Side.class));

        for (Side candidate : CONNECTING_ORDER.get(preferred)) {
            if (!exits.contains(candidate) && !entries.contains(candidate)) {
                exits.add(candidate);
                return candidate;
            }
        }
        exits.add(preferred);
        return preferred;
    }

    private Side assignEntrySide(Bounds nodeBounds, Bounds connectedNodeBounds, String nodeId,
                                 Map<String, Set<Side>> exitSides, Map<String, Set<Side>> entrySides) {
        Side preferred = preferredSide(nodeBounds, connectedNodeBounds);
        Set<Side> exits = exitSides.computeIfAbsent(nodeId, k -> EnumSet.noneOf(Side.class));
        Set<Side> entries = entrySides.computeIfAbsent(nodeId, k -> EnumSet.noneOf(Side.class));

        // Preferred side already has an entry and no exit → share it (arrow heads merge)
        if (entries.contains(preferred) && !exits.contains(preferred)) {
            return preferred;
        }

        // Try a completely free side
        for (Side candidate : CONNECTING_ORDER.get(preferred)) {
            if (!exits.contains(candidate) && !entries.contains(candidate)) {
                entries.add(candidate);
                return candidate;
            }
        }

        // Share any side that already has an entry (but never an exit)
        for (Side candidate : CONNECTING_ORDER.get(preferred)) {
            if (!exits.contains(candidate)) {
                entries.add(candidate);
                return candidate;
            }
        }

        entries.add(preferred);
        return preferred;
    }

    private Side preferredSide(Bounds from, Bounds toward) {
        double dx = centerX(toward) - centerX(from);
        double dy = centerY(toward) - centerY(from);

        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? Side.RIGHT : Side.LEFT;
        } else {
            return dy >= 0 ? Side.BOTTOM : Side.TOP;
        }
    }

    private void replaceConnectors(BpmnModelInstance model, BpmnEdge edge,
                                   double[] sourcePoint, double[] targetPoint,
                                   Side exitSide, Side entrySide) {
        for (Waypoint existingWaypoint : new ArrayList<>(edge.getWaypoints())) {
            edge.removeChildElement(existingWaypoint);
        }

        double sourceX = sourcePoint[0], sourceY = sourcePoint[1];
        double targetX = targetPoint[0], targetY = targetPoint[1];

        addConnector(model, edge, sourceX, sourceY);

        boolean exitHorizontal = isHorizontal(exitSide);
        boolean bothSameAxis = exitHorizontal == isHorizontal(entrySide);
        boolean alreadyAligned = exitHorizontal ? Math.abs(sourceY - targetY) <= 1.0 : Math.abs(sourceX - targetX) <= 1.0;

        if (!alreadyAligned) {
            if (bothSameAxis) {
                double midpoint = exitHorizontal ? (sourceX + targetX) / 2.0 : (sourceY + targetY) / 2.0;
                if (exitHorizontal) {
                    addConnector(model, edge, midpoint, sourceY);
                    addConnector(model, edge, midpoint, targetY);
                } else {
                    addConnector(model, edge, sourceX, midpoint);
                    addConnector(model, edge, targetX, midpoint);
                }
            } else {
                if (exitHorizontal) {
                    addConnector(model, edge, targetX, sourceY);
                } else {
                    addConnector(model, edge, sourceX, targetY);
                }
            }
        }

        addConnector(model, edge, targetX, targetY);
    }

    private boolean isHorizontal(Side side) {
        return side == Side.LEFT || side == Side.RIGHT;
    }

    private boolean isCircularNode(FlowNode node) {
        return node instanceof StartEvent || node instanceof EndEvent;
    }

    private void addConnector(BpmnModelInstance model, BpmnEdge edge, double x, double y) {
        Waypoint wp = model.newInstance(Waypoint.class);
        wp.setX(x);
        wp.setY(y);
        edge.addChildElement(wp);
    }

}
