package org.rj.modelgen.bpmn.generation.render;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;

import java.util.*;
import java.util.stream.Collectors;

public class BpmnDiagramLayoutOptimizer {

    public enum Side { TOP, RIGHT, BOTTOM, LEFT }

    private static final Map<Side, Side[]> SIDE_PREFERENCE = Map.of(
            Side.RIGHT,  new Side[]{Side.RIGHT,  Side.BOTTOM, Side.TOP,    Side.LEFT},
            Side.LEFT,   new Side[]{Side.LEFT,   Side.TOP,    Side.BOTTOM, Side.RIGHT},
            Side.BOTTOM, new Side[]{Side.BOTTOM, Side.RIGHT,  Side.LEFT,   Side.TOP},
            Side.TOP,    new Side[]{Side.TOP,    Side.LEFT,   Side.RIGHT,  Side.BOTTOM}
    );

    public record LayoutScope(Set<String> nodeIds, List<BpmnEdge> edges, List<LayoutScope> children) {}

    public record ScaleResult(Map<String, Bounds> boundsById, List<BpmnEdge> routableEdges) {}

    public record EdgeRoute(BpmnEdge edge, FlowNode src, FlowNode tgt,
                             Bounds srcBounds, Bounds tgtBounds,
                             Side exitSide, Side entrySide) {}

    public final BpmnSubprocessLayoutOptimizer subprocessLayoutResolver =
            new BpmnSubprocessLayoutOptimizer();

    public LayoutScope buildScopeTree(BpmnModelInstance model, Map<String, Bounds> boundsById) {
        final Set<String> insideSubprocess = new HashSet<>();
        final List<LayoutScope> childScopes = new ArrayList<>();

        for (SubProcess sp : model.getModelElementsByType(SubProcess.class)) {
            final Set<String> spNodeIds = new LinkedHashSet<>();

            for (FlowElement fe : sp.getFlowElements()) {
                if (fe instanceof FlowNode fn && boundsById.containsKey(fn.getId())) {
                    spNodeIds.add(fn.getId());
                    insideSubprocess.add(fn.getId());
                }
            }

            final List<BpmnEdge> spEdges = new ArrayList<>();
            for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
                if (!(edge.getBpmnElement() instanceof SequenceFlow sf)) continue;
                FlowNode src = sf.getSource();
                if (src != null && spNodeIds.contains(src.getId())) spEdges.add(edge);
            }
            if (!spNodeIds.isEmpty()) childScopes.add(new LayoutScope(spNodeIds, spEdges, List.of()));
        }

        final Set<String> rootNodeIds = new LinkedHashSet<>();

        for (String id : boundsById.keySet()) {
            if (!insideSubprocess.contains(id)) rootNodeIds.add(id);
        }

        final List<BpmnEdge> rootEdges = new ArrayList<>();
        for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
            if (!(edge.getBpmnElement() instanceof SequenceFlow sf)) continue;
            FlowNode src = sf.getSource();
            FlowNode tgt = sf.getTarget();
            if (src != null && rootNodeIds.contains(src.getId()) && tgt != null && rootNodeIds.contains(tgt.getId())) {
                rootEdges.add(edge);
            }
        }
        return new LayoutScope(rootNodeIds, rootEdges, childScopes);
    }

    public void applySpacingMultiplier(BpmnModelInstance model, double multiplier) {
        final ScaleResult scaled = scaleAndDistribute(model, multiplier);

        subprocessLayoutResolver.layout(model, scaled.boundsById);

        final LayoutScope scopeTree = buildScopeTree(model, scaled.boundsById);
        layoutScope(model, scopeTree, scaled.boundsById, multiplier);

        new BpmnObstacleResolver().resolveConnectorObstacles(model, scopeTree, scaled.boundsById);

        snapEndpointsToNodeMidpoints(model, scaled.boundsById);
    }


     // Recursively lay out a single scope to avoid connectors/nodes overlapping
     // Each scope gets its own de-overlap pass, align+route, and (if it is a subprocess container) a container refit to wrap the final child positions.
    private void layoutScope(BpmnModelInstance model, LayoutScope scope, Map<String, Bounds> boundsById, double multiplier) {

        for (LayoutScope child : scope.children()) {
            layoutScope(model, child, boundsById, multiplier);
        }

        final Set<String> scopeIds = scope.nodeIds();

        final Set<String> boundaryEventIds = new HashSet<>();
        for (BoundaryEvent be : model.getModelElementsByType(BoundaryEvent.class)) {
            boundaryEventIds.add(be.getId());
        }

        resolveOverlappedNodes(boundsById, scopeIds, boundaryEventIds);
        alignAndRouteScoped(model, boundsById, scope.edges(), multiplier, scopeIds);

        if (!scope.children().isEmpty()) {
            for (LayoutScope child : scope.children()) {
                for (SubProcess sp : model.getModelElementsByType(SubProcess.class)) {
                    Set<String> spChildIds = new LinkedHashSet<>();
                    for (FlowElement fe : sp.getFlowElements()) {
                        if (fe instanceof FlowNode fn) spChildIds.add(fn.getId());
                    }
                    if (spChildIds.equals(child.nodeIds())) {
                        subprocessLayoutResolver.refit(model, sp.getId(), boundsById);
                        break;
                    }
                }
            }
        }
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

    public void distributeBoundaryEvents(Map<String, List<BpmnShape>> byParent, Map<String, Bounds> boundsById) {
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

    private void resolveOverlappedNodes(Map<String, Bounds> boundsById, Set<String> scopeIds, Set<String> pinnedIds) {
        final double OVERLAP_PADDING = 30.0;

        List<Map.Entry<String, Bounds>> moveable = boundsById.entrySet().stream()
                .filter(e -> scopeIds.contains(e.getKey()) && !pinnedIds.contains(e.getKey()))
                .sorted(Comparator.comparingDouble((Map.Entry<String, Bounds> e) -> e.getValue().getY())
                        .thenComparingDouble(e -> e.getValue().getX()))
                .toList();

        boolean changed = true;
        for (int iter = 0; changed && iter < 20; iter++) {
            changed = false;
            for (int i = 0; i < moveable.size(); i++) {
                Bounds a = moveable.get(i).getValue();
                for (int j = i + 1; j < moveable.size(); j++) {
                    Bounds b = moveable.get(j).getValue();
                    if (a != null && b != null && shapesOverlap(a, b)) {
                        b.setY(a.getY() + a.getHeight() + OVERLAP_PADDING);
                        changed = true;
                    }
                }
            }
        }
    }

    private void alignAndRouteScoped(BpmnModelInstance model,   Map<String, Bounds> boundsById, List<BpmnEdge> edges, double multiplier, Set<String> scopeIds) {
        final Map<String, Set<Side>> usedExits = new HashMap<>();
        final Map<String, Set<Side>> usedEntries = new HashMap<>();

        List<EdgeRoute> routes = new ArrayList<>(edges.size());
        for (BpmnEdge edge : edges) {
            if (!(edge.getBpmnElement() instanceof SequenceFlow sf)) continue;
            FlowNode src = sf.getSource();
            FlowNode tgt = sf.getTarget();
            Bounds srcB = boundsById.get(src.getId());
            Bounds tgtB = boundsById.get(tgt.getId());

            if (srcB == null || tgtB == null) {
                scaleWaypoints(edge, multiplier);
                continue;
            }

            Side exit, entry;
            if (!(src instanceof BoundaryEvent) && isBackwardEdge(srcB, tgtB)) {
                Side loopSide = centerY(tgtB) < centerY(srcB) - 50.0 ? Side.BOTTOM : Side.TOP;
                exit  = loopSide;
                entry = loopSide;
            } else {
                exit  = (src instanceof BoundaryEvent) ? Side.BOTTOM : assignSide(srcB, tgtB, src.getId(), usedExits, usedEntries, true);
                entry = assignSide(tgtB, srcB, tgt.getId(), usedExits, usedEntries, false);
            }
            routes.add(new EdgeRoute(edge, src, tgt, srcB, tgtB, exit, entry));
        }

        final Map<String, Bounds> scopedBounds = new LinkedHashMap<>();
        for (String id : scopeIds) {
            Bounds b = boundsById.get(id);
            if (b != null) scopedBounds.put(id, b);
        }

        Set<String> alignedCircular = new HashSet<>();
        Set<String> alignedDiamond  = new HashSet<>();

        for (EdgeRoute r : routes) {
            alignY(r.src, r.srcBounds, r.tgt, r.tgtBounds, scopedBounds, alignedCircular, alignedDiamond);
            alignY(r.tgt, r.tgtBounds, r.src, r.srcBounds, scopedBounds, alignedCircular, alignedDiamond);
            rebuildEdge(model, r.edge, r.srcBounds, r.tgtBounds, r.exitSide, r.entrySide);
        }
    }

    private boolean isOverlappedNode(String excludeId, double x, double y, double w, double h, Map<String, Bounds> scopedBounds) {
        for (var entry : scopedBounds.entrySet()) {
            if (entry.getKey().equals(excludeId)) continue;
            Bounds o = entry.getValue();
            if (x < o.getX() + o.getWidth() && x + w > o.getX()
                    && y < o.getY() + o.getHeight() && y + h > o.getY()) {
                return true;
            }
        }
        return false;
    }

    public static boolean shapesOverlap(Bounds a, Bounds b) {
        return a.getX() < b.getX() + b.getWidth()  && a.getX() + a.getWidth()  > b.getX()
                && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY();
    }

    private static boolean isBackwardEdge(Bounds srcB, Bounds tgtB) {
        return centerX(tgtB) < centerX(srcB) - 100.0;
    }

    public void alignY(FlowNode node, Bounds nodeBounds, FlowNode neighbor, Bounds neighborBounds, Map<String, Bounds> allBounds, Set<String> alignedCircular, Set<String> alignedDiamond) {
        if (nodeBounds == null || neighborBounds == null) return;

        boolean shouldAlign =
                (isCircular(node) && !isCircular(neighbor) && alignedCircular.add(node.getId()))
                        || (isDiamond(node) && !isDiamond(neighbor) && !isCircular(neighbor) && alignedDiamond.add(node.getId()));
        if (!shouldAlign) return;

        double candidateY = centerY(neighborBounds) - nodeBounds.getHeight() / 2.0;
        if (!isOverlappedNode(node.getId(), nodeBounds.getX(), candidateY, nodeBounds.getWidth(), nodeBounds.getHeight(), allBounds)) {
            nodeBounds.setY(candidateY);
        }
    }

    public Side assignSide(Bounds nodeBounds, Bounds peerBounds, String nodeId, Map<String, Set<Side>> exitMap, Map<String, Set<Side>> entryMap, boolean isExit) {
        Side preferred = preferredSide(nodeBounds, peerBounds);
        Set<Side> exits = exitMap.computeIfAbsent(nodeId,  k -> EnumSet.noneOf(Side.class));
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

    private Side preferredSide(Bounds from, Bounds to) {
        double dx = centerX(to) - centerX(from);
        double dy = centerY(to) - centerY(from);
        if (Math.abs(dx) * 2 >= Math.abs(dy)) {
            return dx >= 0 ? Side.RIGHT : Side.LEFT;
        }
        return dy >= 0 ? Side.BOTTOM : Side.TOP;
    }

    public void rebuildEdge(BpmnModelInstance model, BpmnEdge edge, Bounds srcB, Bounds tgtB, Side exitSide, Side entrySide) {
        new ArrayList<>(edge.getWaypoints()).forEach(edge::removeChildElement);

        double[] s = sideMidpoint(srcB, exitSide);
        double[] t = sideMidpoint(tgtB, entrySide);

        boolean exitH = isHorizontal(exitSide);

        addWaypoint(model, edge, s[0], s[1]);

        boolean sameAxis = exitH == isHorizontal(entrySide);
        boolean aligned  = exitH ? Math.abs(s[1] - t[1]) <= 1.0 : Math.abs(s[0] - t[0]) <= 1.0;

        if (!aligned) {
            if (sameAxis) {
                double mid;
                if (exitH) {
                    mid = (exitSide == Side.RIGHT)  ? s[0] + 20.0 : (s[0] + t[0]) / 2.0;
                } else {
                    mid = (s[1] + t[1]) / 2.0;
                    if (Math.abs(s[1] - t[1]) < 1.0) {
                        mid = (exitSide == Side.TOP) ? s[1] - 20.0 : s[1] + 20.0;
                    } else {
                        if (entrySide == Side.TOP && mid >= t[1]) {
                            mid = Math.min(s[1], t[1]) - 20.0;
                        } else if (entrySide == Side.BOTTOM && mid <= t[1]) {
                            mid = Math.max(s[1], t[1]) + 20.0;
                        }
                    }
                }
                addWaypoint(model, edge, exitH ? mid  : s[0], exitH ? s[1] : mid);
                addWaypoint(model, edge, exitH ? mid  : t[0], exitH ? t[1] : mid);
            } else {
                addWaypoint(model, edge, exitH ? t[0] : s[0], exitH ? s[1] : t[1]);
            }
        }

        addWaypoint(model, edge, t[0], t[1]);
    }

    public static double centerX(Bounds b) { return b.getX() + b.getWidth()  / 2.0; }
    public static double centerY(Bounds b) { return b.getY() + b.getHeight() / 2.0; }

    private double[] sideMidpoint(Bounds b, Side side) {
        return switch (side) {
            case TOP    -> new double[]{centerX(b), b.getY()};
            case BOTTOM -> new double[]{centerX(b), b.getY() + b.getHeight()};
            case LEFT   -> new double[]{b.getX(),   centerY(b)};
            case RIGHT  -> new double[]{b.getX() + b.getWidth(), centerY(b)};
        };
    }

    private static boolean isHorizontal(Side s) { return s == Side.LEFT  || s == Side.RIGHT; }
    private static boolean isCircular(FlowNode n) { return n instanceof StartEvent || n instanceof EndEvent; }
    private static boolean isDiamond(FlowNode n)  { return n instanceof Gateway; }

    public void scaleWaypoints(BpmnEdge edge, double multiplier) {
        edge.getWaypoints().forEach(wp -> {
            wp.setX(wp.getX() * multiplier);
            wp.setY(wp.getY() * multiplier);
        });
    }

    public void addWaypoint(BpmnModelInstance model, BpmnEdge edge, double x, double y) {
        Waypoint wp = model.newInstance(Waypoint.class);
        wp.setX(x);
        wp.setY(y);
        edge.addChildElement(wp);
    }

    public void snapEndpointsToNodeMidpoints(BpmnModelInstance model, Map<String, Bounds> boundsById) {
        for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
            if (!(edge.getBpmnElement() instanceof SequenceFlow sf)) continue;
            FlowNode src = sf.getSource();
            FlowNode tgt = sf.getTarget();
            if (src == null || tgt == null) continue;
            Bounds srcB = boundsById.get(src.getId());
            Bounds tgtB = boundsById.get(tgt.getId());
            if (srcB == null || tgtB == null) continue;

            List<Waypoint> wps = new ArrayList<>(edge.getWaypoints());
            int n = wps.size();
            if (n < 2) continue;

            Side srcSide = closestSide(wps.get(0), srcB);
            double[] srcMid = sideMidpointOf(srcSide, srcB);
            wps.get(0).setX(srcMid[0]);
            wps.get(0).setY(srcMid[1]);

            Side tgtSide = closestSide(wps.get(n - 1), tgtB);
            double[] tgtMid = sideMidpointOf(tgtSide, tgtB);
            wps.get(n - 1).setX(tgtMid[0]);
            wps.get(n - 1).setY(tgtMid[1]);

            if (n == 2) {
                if (srcSide == Side.LEFT || srcSide == Side.RIGHT) {
                    wps.get(1).setY(srcMid[1]);
                } else {
                    wps.get(1).setX(srcMid[0]);
                }
            } else {
                if (srcSide == Side.LEFT || srcSide == Side.RIGHT) {
                    wps.get(1).setY(srcMid[1]);
                } else {
                    wps.get(1).setX(srcMid[0]);
                }
                if (tgtSide == Side.LEFT || tgtSide == Side.RIGHT) {
                    wps.get(n - 2).setY(tgtMid[1]);
                } else {
                    wps.get(n - 2).setX(tgtMid[0]);
                }
            }
        }
    }

    private static Side closestSide(Waypoint wp, Bounds b) {
        double distLeft = Math.abs(wp.getX() - b.getX());
        double distRight = Math.abs(wp.getX() - (b.getX() + b.getWidth()));
        double distTop = Math.abs(wp.getY() - b.getY());
        double distBottom = Math.abs(wp.getY() - (b.getY() + b.getHeight()));
        double minDist = Math.min(Math.min(distLeft, distRight), Math.min(distTop, distBottom));

        if (minDist == distLeft) {
            return Side.LEFT;
        }

        if (minDist == distRight) {
            return Side.RIGHT;
        }

        if (minDist == distTop) {
            return Side.TOP;
        }

        return Side.BOTTOM;
    }

    private static double[] sideMidpointOf(Side side, Bounds b) {
        double cx = b.getX() + b.getWidth()  / 2.0;
        double cy = b.getY() + b.getHeight() / 2.0;
        return switch (side) {
            case LEFT   -> new double[]{b.getX(), cy};
            case RIGHT  -> new double[]{b.getX() + b.getWidth(), cy};
            case TOP    -> new double[]{cx, b.getY()};
            case BOTTOM -> new double[]{cx, b.getY() + b.getHeight()};
        };
    }

}
