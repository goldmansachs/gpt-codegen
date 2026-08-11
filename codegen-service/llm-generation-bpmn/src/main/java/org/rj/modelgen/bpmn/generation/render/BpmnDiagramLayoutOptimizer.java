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

    public record LayoutScope(Set<String> nodeIds, List<BpmnEdge> edges, List<LayoutScope> children) {}

    public record ScaleResult(Map<String, Bounds> boundsById, List<BpmnEdge> routableEdges) {}

    public record EdgeRoute(BpmnEdge edge, FlowNode src, FlowNode tgt,
                             Bounds srcBounds, Bounds tgtBounds,
                             Side exitSide, Side entrySide) {}

    private static final double CHAIN_ROW_SPACING = 130.0;
    private static final double NEIGHBOR_GAP = 50.0;
    private static final double BOUNDARY_EVENT_SOLO_FRAC = 0.25;
    private static final double CENTER_FRAC = 0.5;
    private static final double CONTESTED_EXIT_FRAC = 0.8;

    private static final double INSET = 0.5;

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

        subprocessLayoutResolver.layout(model, scaled.boundsById());
        redistributeSubProcessBoundaryEvents(model, scaled.boundsById());
        positionBoundaryEventChainsLocally(model, scaled.boundsById());
        positionTerminalNodesNearSource(model, scaled.boundsById());

        final LayoutScope scopeTree = buildScopeTree(model, scaled.boundsById());
        layoutScope(model, scopeTree, scaled.boundsById(), multiplier);

        new BpmnObstacleResolver().resolveConnectorObstacles(model, scopeTree, scaled.boundsById());
    }

    private void positionBoundaryEventChainsLocally(BpmnModelInstance model, Map<String, Bounds> boundsById) {
        final Set<String> mainFlowIds = computeMainFlowReachableIds(model);

        for (BoundaryEvent be : model.getModelElementsByType(BoundaryEvent.class)) {
            final Bounds beBounds = boundsById.get(be.getId());

            if (beBounds == null) continue;

            double originX = beBounds.getX(), originY = beBounds.getY();
            final double originW = beBounds.getWidth(), originH = beBounds.getHeight();
            final Activity host = be.getAttachedTo();

            if (host instanceof SubProcess) {
                final Bounds hostBounds = boundsById.get(host.getId());
                if (hostBounds != null) {
                    final double hostTop = hostBounds.getY();
                    final double hostBottom = hostTop + hostBounds.getHeight();
                    final double beCenterY = originY + originH / 2.0;
                    if (beCenterY >= hostBottom - 1.0) {
                        originY = hostBottom + NEIGHBOR_GAP;
                    } else if (beCenterY <= hostTop + 1.0) {
                        originY = hostTop - NEIGHBOR_GAP - originH;
                    }
                }
            }

            final Set<String> localVisited = new HashSet<>();
            final Deque<double[]> pendingOrigin = new ArrayDeque<>();
            final Deque<String> pendingId = new ArrayDeque<>();
            final List<FlowNode> beTargets = new ArrayList<>();

            for (SequenceFlow sf : be.getOutgoing()) {
                FlowNode target = sf.getTarget();
                if (target != null && !mainFlowIds.contains(target.getId())) beTargets.add(target);
            }

            enqueueFanned(beTargets, new double[]{originX, originY, originW, originH}, boundsById, pendingId, pendingOrigin);

            while (!pendingId.isEmpty()) {
                final String nodeId = pendingId.removeFirst();
                final double[] pred = pendingOrigin.removeFirst();

                if (!localVisited.add(nodeId) || mainFlowIds.contains(nodeId)) continue;

                final double[] placed = placeAfter(boundsById.get(nodeId), pred);

                final Object el = model.getModelElementById(nodeId);
                if (el instanceof FlowNode node) {
                    final List<FlowNode> children = new ArrayList<>();
                    for (SequenceFlow sf : node.getOutgoing()) {
                        FlowNode next = sf.getTarget();
                        if (next != null) children.add(next);
                    }
                    enqueueFanned(children, placed, boundsById, pendingId, pendingOrigin);
                }
            }
        }
    }

    private void positionTerminalNodesNearSource(BpmnModelInstance model, Map<String, Bounds> boundsById) {
        final Map<String, List<FlowNode>> terminalsBySource = new LinkedHashMap<>();
        for (FlowNode node : model.getModelElementsByType(FlowNode.class)) {
            if (!node.getOutgoing().isEmpty() || node.getIncoming().size() != 1) continue;
            FlowNode src = node.getIncoming().iterator().next().getSource();
            if (src != null) terminalsBySource.computeIfAbsent(src.getId(), k -> new ArrayList<>()).add(node);
        }

        for (var entry : terminalsBySource.entrySet()) {
            final Bounds srcB = boundsById.get(entry.getKey());
            if (srcB == null) continue;

            final Object srcEl = model.getModelElementById(entry.getKey());
            double originY = srcB.getY();
            if (srcEl instanceof SubProcess sp && subProcessSideIsContested(sp, Side.RIGHT, boundsById)) {
                originY = srcB.getY() + srcB.getHeight() * CONTESTED_EXIT_FRAC - srcB.getHeight() / 2.0;
            }

            final Deque<String> ids = new ArrayDeque<>();
            final Deque<double[]> origins = new ArrayDeque<>();
            enqueueFanned(entry.getValue(), new double[]{srcB.getX(), originY, srcB.getWidth(), srcB.getHeight()}, boundsById, ids, origins);
            while (!ids.isEmpty()) placeAfter(boundsById.get(ids.removeFirst()), origins.removeFirst());
        }
    }

    public Set<String> computeContestedExitKeys(List<EdgeRoute> routes) {
        final Set<String> entryKeys = new HashSet<>(), exitKeys = new HashSet<>();
        for (EdgeRoute r : routes) {
            entryKeys.add(r.tgt().getId() + "|" + r.entrySide());
            exitKeys.add(r.src().getId() + "|" + r.exitSide());
        }
        exitKeys.retainAll(entryKeys);
        return exitKeys;
    }

    private boolean subProcessSideIsContested(SubProcess sp, Side side, Map<String, Bounds> boundsById) {
        final Bounds spB = boundsById.get(sp.getId());
        if (spB == null) return false;
        for (SequenceFlow sf : sp.getIncoming()) {
            final FlowNode from = sf.getSource();
            final Bounds fromB = from != null ? boundsById.get(from.getId()) : null;
            if (fromB != null && nearestSide(fromB, spB) == side) return true;
        }
        return false;
    }

    private static void enqueueFanned(List<FlowNode> targets, double[] origin, Map<String, Bounds> boundsById,
                                       Deque<String> pendingId, Deque<double[]> pendingOrigin) {
        final List<double[]> origins = fanOutOrigins(origin, targets.size());
        for (int i = 0; i < targets.size(); i++) {
            pendingId.add(targets.get(i).getId());
            pendingOrigin.add(origins.get(i));
        }
    }

   private static double[] placeAfter(Bounds nodeBounds, double[] pred) {
        final double predX = pred[0], predY = pred[1], predW = pred[2], predH = pred[3];
        final double newX = predX + predW + NEIGHBOR_GAP;
        final double thisH = nodeBounds != null ? nodeBounds.getHeight() : predH;
        final double newY = predY + (predH - thisH) / 2.0;
        if (nodeBounds != null) {
            nodeBounds.setX(newX);
            nodeBounds.setY(newY);
        }
        final double thisW = nodeBounds != null ? nodeBounds.getWidth() : predW;
        return new double[]{newX, newY, thisW, thisH};
    }

    private static List<double[]> fanOutOrigins(double[] origin, int count) {
        final List<double[]> result = new ArrayList<>(count);
        if (count <= 1) {
            result.add(origin);
            return result;
        }
        final double centerY = origin[1] + origin[3] / 2.0;
        for (int i = 0; i < count; i++) {
            final double offset = (i - (count - 1) / 2.0) * CHAIN_ROW_SPACING;
            result.add(new double[]{origin[0], centerY + offset - origin[3] / 2.0, origin[2], origin[3]});
        }
        return result;
    }

    private Set<String> computeMainFlowReachableIds(BpmnModelInstance model) {
        final Map<String, List<String>> adjacency = new HashMap<>();
        for (SequenceFlow sf : model.getModelElementsByType(SequenceFlow.class)) {
            FlowNode src = sf.getSource();
            FlowNode tgt = sf.getTarget();
            if (src == null || tgt == null || src instanceof BoundaryEvent) continue;
            adjacency.computeIfAbsent(src.getId(), k -> new ArrayList<>()).add(tgt.getId());
        }

        final Set<String> reachable = new HashSet<>();
        final Deque<String> queue = new ArrayDeque<>();
        for (FlowNode fn : model.getModelElementsByType(FlowNode.class)) {
            if (fn instanceof BoundaryEvent) continue;
            if (fn.getIncoming().isEmpty() && reachable.add(fn.getId())) queue.add(fn.getId());
        }
        while (!queue.isEmpty()) {
            for (String succ : adjacency.getOrDefault(queue.removeFirst(), List.of())) {
                if (reachable.add(succ)) queue.add(succ);
            }
        }
        return reachable;
    }


     // Recursively lay out a single scope to avoid connectors/nodes overlapping
     // Each scope gets its own de-overlap pass, align+route, and (if it is a subprocess container) a container refit to wrap the final child positions.
    private void layoutScope(BpmnModelInstance model, LayoutScope scope, Map<String, Bounds> boundsById, double multiplier) {

        for (LayoutScope child : scope.children()) {
            layoutScope(model, child, boundsById, multiplier);
        }

        final Set<String> scopeIds = scope.nodeIds();

        final Set<String> immovableIds = new HashSet<>();

        for (BoundaryEvent be : model.getModelElementsByType(BoundaryEvent.class)) {
            immovableIds.add(be.getId());
        }

        for (SubProcess sp : model.getModelElementsByType(SubProcess.class)) {
            immovableIds.add(sp.getId());
        }

        for (int i = 0; i < 5; i++) {
            boolean overlapChanged = resolveOverlappedNodes(model, boundsById, scopeIds, immovableIds);
            boolean alignChanged = alignCenters(model, boundsById, scope.edges(), Set.of());
            if (!overlapChanged && !alignChanged) break;
        }

        routeScoped(model, boundsById, scope.edges(), multiplier);

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

    public void redistributeSubProcessBoundaryEvents(BpmnModelInstance model, Map<String, Bounds> boundsById) {
        final Map<String, List<BpmnShape>> byParent = new LinkedHashMap<>();
        for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
            if (shape.getBpmnElement() instanceof BoundaryEvent be && be.getAttachedTo() instanceof SubProcess sp) {
                byParent.computeIfAbsent(sp.getId(), k -> new ArrayList<>()).add(shape);
            }
        }
        distributeBoundaryEvents(byParent, boundsById);
    }

    public void distributeBoundaryEvents(Map<String, List<BpmnShape>> byParent, Map<String, Bounds> boundsById) {
        for (var entry : byParent.entrySet()) {
            Bounds parentBounds = boundsById.get(entry.getKey());
            if (parentBounds == null) continue;

            List<BpmnShape> shapes = entry.getValue();
            boolean onSubProcess = shapes.get(0).getBpmnElement() instanceof BoundaryEvent be
                    && be.getAttachedTo() instanceof SubProcess;

            if (onSubProcess && shapes.size() > 2) {
                int perSide = (shapes.size() + 2) / 3;
                placeAlongSide(shapes.subList(0, Math.min(perSide, shapes.size())), parentBounds, Side.BOTTOM);
                placeAlongSide(shapes.subList(Math.min(perSide, shapes.size()), Math.min(2 * perSide, shapes.size())), parentBounds, Side.RIGHT);
                placeAlongSide(shapes.subList(Math.min(2 * perSide, shapes.size()), shapes.size()), parentBounds, Side.TOP);
            } else {
                placeAlongSide(shapes, parentBounds, Side.BOTTOM);
            }
        }
    }

    private void placeAlongSide(List<BpmnShape> shapes, Bounds parentBounds, Side side) {
        final int n = shapes.size();
        if (n == 0) return;

        if (isHorizontal(side)) {
            final double x = (side == Side.RIGHT ? parentBounds.getX() + parentBounds.getWidth() : parentBounds.getX());
            final double[] centers = sideCenters(parentBounds.getHeight(), maxExtent(shapes, true), n, side);
            for (int i = 0; i < n; i++) {
                Bounds b = shapes.get(i).getBounds();
                b.setX(x - b.getWidth() / 2.0);
                b.setY(parentBounds.getY() + centers[i] - b.getHeight() / 2.0);
            }
        } else {
            final double y = (side == Side.BOTTOM ? parentBounds.getY() + parentBounds.getHeight() : parentBounds.getY());
            final double[] centers = sideCenters(parentBounds.getWidth(), maxExtent(shapes, false), n, side);
            for (int i = 0; i < n; i++) {
                Bounds b = shapes.get(i).getBounds();
                b.setX(parentBounds.getX() + centers[i] - b.getWidth() / 2.0);
                b.setY(y - b.getHeight() / 2.0);
            }
        }
    }

    private static double maxExtent(List<BpmnShape> shapes, boolean height) {
        return shapes.stream().mapToDouble(s -> height ? s.getBounds().getHeight() : s.getBounds().getWidth()).max().orElse(0);
    }

    private static double[] sideCenters(double axisLength, double itemExtent, int n, Side side) {
        if (n == 1) return new double[]{axisLength * sideFraction(side, 0, 1)};
        final double naturalSpacing = axisLength / (n + 1);
        final double spacing = Math.max(naturalSpacing, itemExtent + 10.0);
        final double start = (axisLength - spacing * (n + 1)) / 2.0;
        final double[] centers = new double[n];
        for (int i = 0; i < n; i++) centers[i] = start + spacing * (i + 1);
        return centers;
    }

    private static double sideFraction(Side side, int i, int n) {
        if (side == Side.RIGHT && n == 1) return BOUNDARY_EVENT_SOLO_FRAC;
        return (double) (i + 1) / (n + 1);
    }

    private boolean resolveOverlappedNodes(BpmnModelInstance model, Map<String, Bounds> boundsById, Set<String> scopeIds, Set<String> pinnedIds) {
        final double OVERLAP_PADDING = 30.0;

        final Map<String, String> boundaryEventHost = new HashMap<>();
        for (BoundaryEvent be : model.getModelElementsByType(BoundaryEvent.class)) {
            Activity host = be.getAttachedTo();
            if (host != null) boundaryEventHost.put(be.getId(), host.getId());
        }

        List<Map.Entry<String, Bounds>> all = boundsById.entrySet().stream()
                .filter(e -> scopeIds.contains(e.getKey()))
                .sorted(Comparator.comparingDouble((Map.Entry<String, Bounds> e) -> e.getValue().getY())
                        .thenComparingDouble(e -> e.getValue().getX()))
                .toList();

        final Set<String> settledAgainstPinned = new HashSet<>();

        boolean changedOverall = false;
        boolean changed = true;
        for (int iter = 0; changed && iter < 20; iter++) {
            changed = false;
            for (int i = 0; i < all.size(); i++) {
                final String aId = all.get(i).getKey();
                final Bounds a = all.get(i).getValue();
                for (int j = i + 1; j < all.size(); j++) {
                    final String bId = all.get(j).getKey();
                    final Bounds b = all.get(j).getValue();
                    if (a == null || b == null || !shapesOverlap(a, b)) continue;

                    if (aId.equals(boundaryEventHost.get(bId)) || bId.equals(boundaryEventHost.get(aId))) continue;

                    final boolean aPinned = pinnedIds.contains(aId) || settledAgainstPinned.contains(aId);
                    final boolean bPinned = pinnedIds.contains(bId) || settledAgainstPinned.contains(bId);
                    if (aPinned && bPinned) continue;

                    if (!aPinned && !bPinned) {
                        b.setY(a.getY() + a.getHeight() + OVERLAP_PADDING);
                    } else if (bPinned) {
                        a.setY(centerY(a) <= centerY(b)
                                ? b.getY() - a.getHeight() - OVERLAP_PADDING
                                : b.getY() + b.getHeight() + OVERLAP_PADDING);
                        if (pinnedIds.contains(bId)) settledAgainstPinned.add(aId);
                    } else {
                        b.setY(centerY(b) <= centerY(a)
                                ? a.getY() - b.getHeight() - OVERLAP_PADDING
                                : a.getY() + a.getHeight() + OVERLAP_PADDING);
                        if (pinnedIds.contains(aId)) settledAgainstPinned.add(bId);
                    }
                    changed = true;
                    changedOverall = true;
                }
            }
        }
        return changedOverall;
    }

    private void routeScoped(BpmnModelInstance model, Map<String, Bounds> boundsById, List<BpmnEdge> edges, double multiplier) {
        final List<EdgeRoute> routes = new ArrayList<>(edges.size());
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

            Side[] sides = computeSides(src, srcB, tgt, tgtB, boundsById);
            routes.add(new EdgeRoute(edge, src, tgt, srcB, tgtB, sides[0], sides[1]));
        }

        final Set<String> contestedExitKeys = computeContestedExitKeys(routes);
        final Map<BpmnEdge, double[]> entryPoints = computeEntryPoints(routes);
        final Map<BpmnEdge, double[]> exitPoints = computeExitPoints(routes, contestedExitKeys);
        for (EdgeRoute r : routes) {
            double[] s = exitPoints.get(r.edge);
            double[] t = entryPoints.get(r.edge);
            routeOrthogonal(model, r.edge, s, r.exitSide, t, r.entrySide);
        }
    }

    public Map<BpmnEdge, double[]> computeEntryPoints(List<EdgeRoute> routes) {
        return computeSidePoints(routes, r -> r.tgt().getId(), EdgeRoute::entrySide, EdgeRoute::tgtBounds, EdgeRoute::srcBounds, EdgeRoute::tgt, Set.of());
    }

    public Map<BpmnEdge, double[]> computeExitPoints(List<EdgeRoute> routes, Set<String> contestedExitKeys) {
        return computeSidePoints(routes, r -> r.src().getId(), EdgeRoute::exitSide, EdgeRoute::srcBounds, EdgeRoute::tgtBounds, EdgeRoute::src, contestedExitKeys);
    }

    private Map<BpmnEdge, double[]> computeSidePoints(List<EdgeRoute> routes,
            java.util.function.Function<EdgeRoute, String> nodeId,
            java.util.function.Function<EdgeRoute, Side> side,
            java.util.function.Function<EdgeRoute, Bounds> selfBounds,
            java.util.function.Function<EdgeRoute, Bounds> neighborBounds,
            java.util.function.Function<EdgeRoute, FlowNode> selfNode,
            Set<String> contestedKeys) {
        final Map<String, List<EdgeRoute>> groups = new LinkedHashMap<>();
        for (EdgeRoute r : routes) {
            groups.computeIfAbsent(nodeId.apply(r) + "|" + side.apply(r), k -> new ArrayList<>()).add(r);
        }

        final Map<BpmnEdge, double[]> result = new HashMap<>();
        for (var groupEntry : groups.entrySet()) {
            final List<EdgeRoute> group = groupEntry.getValue();
            final Side s = side.apply(group.get(0));
            final Bounds selfB = selfBounds.apply(group.get(0));
            final boolean horizontalSide = (s == Side.LEFT || s == Side.RIGHT);
            final FlowNode node = selfNode.apply(group.get(0));
            final boolean pointShape = (node instanceof Gateway) || (node instanceof Event);
            final boolean contested = contestedKeys.contains(groupEntry.getKey());

            // Stable order along the side axis (by neighbour position) to reduce crossings.
            group.sort(Comparator.comparingDouble(r ->
                    horizontalSide ? centerY(neighborBounds.apply(r)) : centerX(neighborBounds.apply(r))));

            final int k = group.size();
            for (int i = 0; i < k; i++) {
                final double frac;
                if (pointShape) {
                    frac = CENTER_FRAC;
                } else if (contested && k == 1) {
                    frac = CONTESTED_EXIT_FRAC;
                } else {
                    frac = (i + 1.0) / (k + 1.0);
                }
                final double[] pt = switch (s) {
                    case TOP    -> new double[]{selfB.getX() + selfB.getWidth() * frac, selfB.getY()};
                    case BOTTOM -> new double[]{selfB.getX() + selfB.getWidth() * frac, selfB.getY() + selfB.getHeight()};
                    case LEFT   -> new double[]{selfB.getX(), selfB.getY() + selfB.getHeight() * frac};
                    case RIGHT  -> new double[]{selfB.getX() + selfB.getWidth(), selfB.getY() + selfB.getHeight() * frac};
                };
                result.put(group.get(i).edge(), pt);
            }
        }
        return result;
    }

    public void routeOrthogonal(BpmnModelInstance model, BpmnEdge edge,
                                double[] s, Side exitSide, double[] t, Side entrySide) {
        new ArrayList<>(edge.getWaypoints()).forEach(edge::removeChildElement);

        final boolean exitH = isHorizontal(exitSide);
        final boolean entryH = isHorizontal(entrySide);

        final List<double[]> pts = new ArrayList<>(4);
        pts.add(s);

        if (exitH != entryH) {
            // L-shape: single corner where the two axes meet.
            pts.add(exitH ? new double[]{t[0], s[1]} : new double[]{s[0], t[1]});
        } else if (exitH) {
            // Both horizontal: C/Z with a shared vertical mid-line.
            if (Math.abs(s[1] - t[1]) > 1.0) {
                double mx = (s[0] + t[0]) / 2.0;
                pts.add(new double[]{mx, s[1]});
                pts.add(new double[]{mx, t[1]});
            }
        } else {
            // Both vertical: C/Z with a shared horizontal mid-line.
            if (Math.abs(s[0] - t[0]) > 1.0) {
                double my = (s[1] + t[1]) / 2.0;
                pts.add(new double[]{s[0], my});
                pts.add(new double[]{t[0], my});
            }
        }

        pts.add(t);

        // Emit waypoints, collapsing any zero-length segments.
        double[] prev = null;
        for (double[] p : pts) {
            if (prev != null && Math.abs(prev[0] - p[0]) < 0.5 && Math.abs(prev[1] - p[1]) < 0.5) continue;
            addWaypoint(model, edge, p[0], p[1]);
            prev = p;
        }
    }

    public boolean alignCenters(BpmnModelInstance model, Map<String, Bounds> boundsById, List<BpmnEdge> edges, Set<String> excludedIds) {
        final Set<String> aligned = new HashSet<>();
        final Set<String> containerIds = new HashSet<>();
        for (SubProcess sp : model.getModelElementsByType(SubProcess.class)) containerIds.add(sp.getId());

        boolean changed = false;
        for (BpmnEdge edge : edges) {
            if (!(edge.getBpmnElement() instanceof SequenceFlow sf)) continue;
            FlowNode src = sf.getSource();
            FlowNode tgt = sf.getTarget();
            if (src == null || tgt == null) continue;
            Bounds srcB = boundsById.get(src.getId());
            Bounds tgtB = boundsById.get(tgt.getId());
            if (srcB == null || tgtB == null) continue;
            if (!excludedIds.contains(src.getId()) && alignY(src, srcB, tgt, tgtB, boundsById, aligned, containerIds)) changed = true;
            if (!excludedIds.contains(tgt.getId()) && alignY(tgt, tgtB, src, srcB, boundsById, aligned, containerIds)) changed = true;
        }
        return changed;
    }

    private boolean alignY(FlowNode node, Bounds nodeBounds, FlowNode neighbor, Bounds neighborBounds,
                       Map<String, Bounds> allBounds, Set<String> aligned, Set<String> ignoredIds) {
        if (nodeBounds == null || neighborBounds == null) return false;
        if (rank(node) >= rank(neighbor)) return false;
        if (!aligned.add(node.getId())) return false;

        double candidateY = centerY(neighborBounds) - nodeBounds.getHeight() / 2.0;
        if (Math.abs(candidateY - nodeBounds.getY()) < 0.5) return false;
        if (!isOverlappedNode(node.getId(), nodeBounds.getX(), candidateY, nodeBounds.getWidth(), nodeBounds.getHeight(), allBounds, ignoredIds)) {
            nodeBounds.setY(candidateY);
            return true;
        }
        return false;
    }

    private boolean isOverlappedNode(String excludeId, double x, double y, double w, double h, Map<String, Bounds> allBounds, Set<String> ignoredIds) {
        for (var entry : allBounds.entrySet()) {
            if (entry.getKey().equals(excludeId) || ignoredIds.contains(entry.getKey())) continue;
            Bounds o = entry.getValue();
            if (x < o.getX() + o.getWidth() && x + w > o.getX()
                    && y < o.getY() + o.getHeight() && y + h > o.getY()) {
                return true;
            }
        }
        return false;
    }

    private static int rank(FlowNode n) {
        if (n instanceof Gateway) return 0;
        if (n instanceof BoundaryEvent) return 2;
        if (n instanceof Event) return 1;
        return 2;
    }

    public static boolean shapesOverlap(Bounds a, Bounds b) {
        return a.getX() < b.getX() + b.getWidth()  && a.getX() + a.getWidth()  > b.getX()
                && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY();
    }

    private static boolean isBackwardEdge(Bounds srcB, Bounds tgtB) {
        return centerX(tgtB) < centerX(srcB) - 100.0;
    }

    public Side[] computeSides(FlowNode src, Bounds srcB, FlowNode tgt, Bounds tgtB, Map<String, Bounds> boundsById) {
        if (src instanceof BoundaryEvent be) {
            Activity host = be.getAttachedTo();
            Bounds hostB = host != null ? boundsById.get(host.getId()) : null;
            Side exit = hostB != null ? attachmentSide(srcB, hostB) : Side.BOTTOM;
            return new Side[]{exit, preferredSide(tgtB, srcB)};
        }
        if (tgt instanceof SubProcess) {
            Side entry = nearestSide(srcB, tgtB);
            return new Side[]{opposite(entry), entry};
        }
        if (isBackwardEdge(srcB, tgtB)) {
            boolean targetBelow = centerY(tgtB) >= centerY(srcB);
            return targetBelow ? new Side[]{Side.BOTTOM, Side.TOP} : new Side[]{Side.TOP, Side.BOTTOM};
        }
        return new Side[]{preferredSide(srcB, tgtB), preferredSide(tgtB, srcB)};
    }

    private static Side attachmentSide(Bounds be, Bounds host) {
        double cy = centerY(be);
        if (cy <= host.getY() + INSET) return Side.TOP;
        if (cy >= host.getY() + host.getHeight() - INSET) return Side.BOTTOM;
        return Side.RIGHT;
    }

    private static Side nearestSide(Bounds point, Bounds rect) {
        final double px = centerX(point), py = centerY(point);
        final double left = rect.getX(), right = rect.getX() + rect.getWidth();
        final double top = rect.getY(), bottom = rect.getY() + rect.getHeight();
        final double dLeft = left - px, dRight = px - right, dTop = top - py, dBottom = py - bottom;
        final double best = Math.max(Math.max(dLeft, dRight), Math.max(dTop, dBottom));
        if (best == dLeft) return Side.LEFT;
        if (best == dRight) return Side.RIGHT;
        return best == dTop ? Side.TOP : Side.BOTTOM;
    }

    private static Side opposite(Side s) {
        return switch (s) {
            case TOP -> Side.BOTTOM;
            case BOTTOM -> Side.TOP;
            case LEFT -> Side.RIGHT;
            case RIGHT -> Side.LEFT;
        };
    }

    public Side preferredSide(Bounds from, Bounds to) {
        double dx = centerX(to) - centerX(from);
        double dy = centerY(to) - centerY(from);
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? Side.RIGHT : Side.LEFT;
        }
        return dy >= 0 ? Side.BOTTOM : Side.TOP;
    }

    public static double centerX(Bounds b) { return b.getX() + b.getWidth()  / 2.0; }
    public static double centerY(Bounds b) { return b.getY() + b.getHeight() / 2.0; }

    private static boolean isHorizontal(Side s) { return s == Side.LEFT  || s == Side.RIGHT; }

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
}
