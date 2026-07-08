package org.rj.modelgen.bpmn.generation.render;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;

import java.util.*;

public class BpmnDiagramLayoutOptimizer {

    public enum Side { TOP, RIGHT, BOTTOM, LEFT }

    private static final Map<Side, Side[]> SIDE_PREFERENCE = Map.of(
            Side.RIGHT,  new Side[]{Side.RIGHT,  Side.BOTTOM, Side.TOP,    Side.LEFT},
            Side.LEFT,   new Side[]{Side.LEFT,   Side.TOP,    Side.BOTTOM, Side.RIGHT},
            Side.BOTTOM, new Side[]{Side.BOTTOM, Side.RIGHT,  Side.LEFT,   Side.TOP},
            Side.TOP,    new Side[]{Side.TOP,    Side.LEFT,   Side.RIGHT,  Side.BOTTOM}
    );

    private record ScaleResult(Map<String, Bounds> boundsById,
                               List<BpmnEdge> routableEdges) {}

    private record EdgeRoute(BpmnEdge edge, FlowNode src, FlowNode tgt,
                             Bounds srcBounds, Bounds tgtBounds,
                             Side exitSide, Side entrySide) {}

    private final BpmnSubprocessLayoutOptimizer subprocessLayoutResolver =
            new BpmnSubprocessLayoutOptimizer();

    public void applySpacingMultiplier(BpmnModelInstance model, double multiplier) {
        final ScaleResult scaled = scaleAndDistribute(model, multiplier);
        resolveShapeOverlaps(scaled.boundsById, model);
        subprocessLayoutResolver.layout(model, scaled.boundsById);
        alignAndRoute(model, scaled.boundsById, scaled.routableEdges, multiplier);
    }

    private ScaleResult scaleAndDistribute(BpmnModelInstance model, double multiplier) {
        final Map<String, Bounds> boundsById = new HashMap<>();
        final Map<String, List<BpmnShape>> boundaryEventsByParent = new LinkedHashMap<>();

        for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
            Bounds bounds = shape.getBounds();
            if (bounds == null) continue;

            BaseElement el = shape.getBpmnElement();
            if (el instanceof BoundaryEvent be) {
                Activity parent = be.getAttachedTo();
                if (parent != null) {
                    boundaryEventsByParent
                            .computeIfAbsent(parent.getId(), k -> new ArrayList<>())
                            .add(shape);
                }
            } else {
                bounds.setX(bounds.getX() * multiplier);
                bounds.setY(bounds.getY() * multiplier);
                if (el instanceof SubProcess) {
                    bounds.setWidth(bounds.getWidth() * multiplier);
                    bounds.setHeight(bounds.getHeight() * multiplier);
                }
            }
            if (el != null) boundsById.put(el.getId(), bounds);
        }

        distributeBoundaryEvents(boundaryEventsByParent, boundsById);

        final List<BpmnEdge> routableEdges = new ArrayList<>();
        for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
            BaseElement el = edge.getBpmnElement();
            if (el instanceof SequenceFlow sf) {
                FlowNode src = sf.getSource();
                FlowNode tgt = sf.getTarget();
                boolean hasBounds = src != null && tgt != null
                        && boundsById.containsKey(src.getId())
                        && boundsById.containsKey(tgt.getId());
                if (hasBounds && edge.getWaypoints().size() >= 2) {
                    routableEdges.add(edge);
                } else {
                    scaleWaypoints(edge, multiplier);
                }
            } else {
                scaleWaypoints(edge, multiplier);
            }
        }

        return new ScaleResult(boundsById, routableEdges);
    }

    private void distributeBoundaryEvents(Map<String, List<BpmnShape>> byParent,
                                          Map<String, Bounds> boundsById) {
        for (var entry : byParent.entrySet()) {
            Bounds parentBounds = boundsById.get(entry.getKey());
            if (parentBounds == null) continue;

            List<BpmnShape> shapes = entry.getValue();
            int n = shapes.size();
            double spacing = parentBounds.getWidth() / (n + 1);

            for (int i = 0; i < n; i++) {
                Bounds b = shapes.get(i).getBounds();
                b.setX(parentBounds.getX() + spacing * (i + 1) - b.getWidth() / 2.0);
                b.setY(parentBounds.getY() + parentBounds.getHeight() - b.getHeight() / 2.0);
            }
        }
    }

    private void resolveShapeOverlaps(Map<String, Bounds> boundsById, BpmnModelInstance model) {
        final double OVERLAP_PADDING = 30.0;

        // Collect IDs that must NOT be moved by this pass
        Set<String> excludedIds = new HashSet<>();

        // 1. Boundary events — anchored to parent by distributeBoundaryEvents
        for (BoundaryEvent be : model.getModelElementsByType(BoundaryEvent.class)) {
            excludedIds.add(be.getId());
        }

        // 2. Subprocess containers and their children — managed by BpmnSubprocessLayoutOptimizer
        for (SubProcess sp : model.getModelElementsByType(SubProcess.class)) {
            excludedIds.add(sp.getId());
            for (FlowElement child : sp.getFlowElements()) {
                excludedIds.add(child.getId());
            }
        }

        // Work with moveable main-process-level shapes only, sorted top-to-bottom then left-to-right
        List<Map.Entry<String, Bounds>> moveable = new ArrayList<>(boundsById.entrySet().stream()
                .filter(e -> !excludedIds.contains(e.getKey()))
                .sorted(Comparator.comparingDouble((Map.Entry<String, Bounds> e) -> e.getValue().getY())
                        .thenComparingDouble(e -> e.getValue().getX()))
                .toList());

        // Iteratively push overlapping shapes apart
        boolean changed = true;
        for (int iter = 0; changed && iter < 20; iter++) {
            changed = false;
            for (int i = 0; i < moveable.size(); i++) {
                Bounds a = moveable.get(i).getValue();
                for (int j = i + 1; j < moveable.size(); j++) {
                    Bounds b = moveable.get(j).getValue();
                    if (shapesOverlap(a, b)) {
                        b.setY(a.getY() + a.getHeight() + OVERLAP_PADDING);
                        changed = true;
                    }
                }
            }
        }
    }

    private static boolean shapesOverlap(Bounds a, Bounds b) {
        return a.getX() < b.getX() + b.getWidth()  && a.getX() + a.getWidth()  > b.getX()
            && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY();
    }

    private void alignAndRoute(BpmnModelInstance model,
                               Map<String, Bounds> boundsById,
                               List<BpmnEdge> edges,
                               double multiplier) {
        final Map<String, Set<Side>> usedExits   = new HashMap<>();
        final Map<String, Set<Side>> usedEntries = new HashMap<>();

        List<EdgeRoute> routes = new ArrayList<>(edges.size());
        for (BpmnEdge edge : edges) {
            SequenceFlow sf = (SequenceFlow) edge.getBpmnElement();
            FlowNode src = sf.getSource();
            FlowNode tgt = sf.getTarget();
            Bounds srcB = boundsById.get(src.getId());
            Bounds tgtB = boundsById.get(tgt.getId());

            if (srcB == null || tgtB == null) {
                scaleWaypoints(edge, multiplier);
                continue;
            }

            Side exit = (src instanceof BoundaryEvent)
                    ? Side.BOTTOM
                    : assignSide(srcB, tgtB, src.getId(), usedExits, usedEntries, true);
            Side entry = assignSide(tgtB, srcB, tgt.getId(), usedExits, usedEntries, false);
            routes.add(new EdgeRoute(edge, src, tgt, srcB, tgtB, exit, entry));
        }

        Set<String> alignedCircular = new HashSet<>();
        Set<String> alignedDiamond  = new HashSet<>();

        for (EdgeRoute r : routes) {
            tryAlignY(r.src, r.srcBounds, r.tgt, r.tgtBounds,
                      boundsById, alignedCircular, alignedDiamond);
            tryAlignY(r.tgt, r.tgtBounds, r.src, r.srcBounds,
                      boundsById, alignedCircular, alignedDiamond);

            rebuildEdge(model, r.edge, r.srcBounds, r.tgtBounds, r.exitSide, r.entrySide);
        }
    }

    private void tryAlignY(FlowNode node, Bounds nodeBounds,
                           FlowNode neighbor, Bounds neighborBounds,
                           Map<String, Bounds> allBounds,
                           Set<String> alignedCircular,
                           Set<String> alignedDiamond) {
        if (nodeBounds == null || neighborBounds == null) return;

        boolean shouldAlign =
                (isCircular(node) && !isCircular(neighbor) && alignedCircular.add(node.getId()))
             || (isDiamond(node) && !isDiamond(neighbor) && !isCircular(neighbor) && alignedDiamond.add(node.getId()));
        if (!shouldAlign) return;

        double candidateY = centerY(neighborBounds) - nodeBounds.getHeight() / 2.0;
        if (!wouldOverlap(node.getId(), nodeBounds.getX(), candidateY,
                          nodeBounds.getWidth(), nodeBounds.getHeight(), allBounds)) {
            nodeBounds.setY(candidateY);
        }
    }

    private Side assignSide(Bounds nodeBounds, Bounds peerBounds,
                            String nodeId,
                            Map<String, Set<Side>> exitMap,
                            Map<String, Set<Side>> entryMap,
                            boolean isExit) {
        Side preferred = preferredSide(nodeBounds, peerBounds);
        Set<Side> exits   = exitMap.computeIfAbsent(nodeId,  k -> EnumSet.noneOf(Side.class));
        Set<Side> entries = entryMap.computeIfAbsent(nodeId, k -> EnumSet.noneOf(Side.class));
        Set<Side> own = isExit ? exits : entries;

        if (!isExit && entries.contains(preferred) && !exits.contains(preferred)) {
            return preferred;
        }

        for (Side s : SIDE_PREFERENCE.get(preferred)) {
            if (!exits.contains(s) && !entries.contains(s)) {
                own.add(s);
                return s;
            }
        }

        if (!isExit) {
            for (Side s : SIDE_PREFERENCE.get(preferred)) {
                if (!exits.contains(s)) {
                    entries.add(s);
                    return s;
                }
            }
        }

        own.add(preferred);
        return preferred;
    }

    private Side preferredSide(Bounds from, Bounds toward) {
        double dx = centerX(toward) - centerX(from);
        double dy = centerY(toward) - centerY(from);
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? Side.RIGHT : Side.LEFT;
        }
        return dy >= 0 ? Side.BOTTOM : Side.TOP;
    }

    private void rebuildEdge(BpmnModelInstance model, BpmnEdge edge,
                             Bounds srcB, Bounds tgtB,
                             Side exitSide, Side entrySide) {
        new ArrayList<>(edge.getWaypoints()).forEach(edge::removeChildElement);

        double[] s = sideMidpoint(srcB, exitSide);
        double[] t = sideMidpoint(tgtB, entrySide);

        addWaypoint(model, edge, s[0], s[1]);

        boolean exitH    = isHorizontal(exitSide);
        boolean sameAxis = exitH == isHorizontal(entrySide);
        boolean aligned  = exitH
                ? Math.abs(s[1] - t[1]) <= 1.0
                : Math.abs(s[0] - t[0]) <= 1.0;

        if (!aligned) {
            if (sameAxis) {
                double mid = exitH ? (s[0] + t[0]) / 2.0 : (s[1] + t[1]) / 2.0;
                addWaypoint(model, edge, exitH ? mid  : s[0], exitH ? s[1] : mid);
                addWaypoint(model, edge, exitH ? mid  : t[0], exitH ? t[1] : mid);
            } else {
                addWaypoint(model, edge, exitH ? t[0] : s[0], exitH ? s[1] : t[1]);
            }
        }

        addWaypoint(model, edge, t[0], t[1]);
    }

    private static double centerX(Bounds b) { return b.getX() + b.getWidth()  / 2.0; }
    private static double centerY(Bounds b) { return b.getY() + b.getHeight() / 2.0; }

    private double[] sideMidpoint(Bounds b, Side side) {
        return switch (side) {
            case TOP    -> new double[]{centerX(b), b.getY()};
            case BOTTOM -> new double[]{centerX(b), b.getY() + b.getHeight()};
            case LEFT   -> new double[]{b.getX(),   centerY(b)};
            case RIGHT  -> new double[]{b.getX() + b.getWidth(), centerY(b)};
        };
    }

    private boolean wouldOverlap(String excludeId,
                                 double x, double y, double w, double h,
                                 Map<String, Bounds> boundsById) {
        for (var entry : boundsById.entrySet()) {
            if (entry.getKey().equals(excludeId)) continue;
            Bounds o = entry.getValue();
            if (x < o.getX() + o.getWidth()  && x + w > o.getX()
             && y < o.getY() + o.getHeight() && y + h > o.getY()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHorizontal(Side s) { return s == Side.LEFT  || s == Side.RIGHT; }
    private static boolean isCircular(FlowNode n) { return n instanceof StartEvent || n instanceof EndEvent; }
    private static boolean isDiamond(FlowNode n)  { return n instanceof Gateway; }

    private void scaleWaypoints(BpmnEdge edge, double multiplier) {
        edge.getWaypoints().forEach(wp -> {
            wp.setX(wp.getX() * multiplier);
            wp.setY(wp.getY() * multiplier);
        });
    }

    private void addWaypoint(BpmnModelInstance model, BpmnEdge edge, double x, double y) {
        Waypoint wp = model.newInstance(Waypoint.class);
        wp.setX(x);
        wp.setY(y);
        edge.addChildElement(wp);
    }
}
