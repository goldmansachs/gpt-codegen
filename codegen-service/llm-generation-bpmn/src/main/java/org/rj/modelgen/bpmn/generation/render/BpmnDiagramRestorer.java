package org.rj.modelgen.bpmn.generation.render;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;

import java.util.*;

import static org.rj.modelgen.bpmn.generation.render.BpmnDiagramLayoutOptimizer.*;


// Incremental layout pass for copilot re-render
// Nodes keep their original coordinates & new nodes are added relative to their neighbours.
// Creates new canvas  for forward generation
public class BpmnDiagramRestorer {
    private static final String ID = "id";

    public void applyIncrementalLayout(BpmnModelInstance model, BpmnOriginalCanvas canvas, double multiplier) {
        BpmnDiagramLayoutOptimizer bpmnDiagramLayoutOptimizer = new BpmnDiagramLayoutOptimizer();

        if (canvas == null || canvas.isEmpty()) {
            bpmnDiagramLayoutOptimizer.applySpacingMultiplier(model, multiplier);
            return;
        }

        final BpmnDiagramLayoutOptimizer.ScaleResult scaled = scaleAndDistributeIncrementally(bpmnDiagramLayoutOptimizer, model, multiplier, canvas);

        final Set<String> pinnedIds = new HashSet<>();
        for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
            BaseElement el = shape.getBpmnElement();
            if (el != null && canvas.hasNode(el.getId())) pinnedIds.add(el.getId());
        }

        addNode(scaled.boundsById(), model);

        resolveShapeOverlapsIncrementally(scaled.boundsById(), model, pinnedIds);
        bpmnDiagramLayoutOptimizer.subprocessLayoutResolver.layout(model, scaled.boundsById());
        bpmnDiagramLayoutOptimizer.redistributeSubProcessBoundaryEvents(model, scaled.boundsById());
        alignAndRouteIncrementally(bpmnDiagramLayoutOptimizer, model, scaled.boundsById(), scaled.routableEdges(), multiplier, pinnedIds, canvas);
        final BpmnDiagramLayoutOptimizer.LayoutScope scopeTree = bpmnDiagramLayoutOptimizer.buildScopeTree(model, scaled.boundsById());
        new BpmnObstacleResolver().resolveConnectorObstacles(model, scopeTree, scaled.boundsById());

        // Restore diagram element ids and swimlane pools from the original canvas
        restoreDiagramElementIds(model, canvas);
        if (!canvas.getParticipantShapes().isEmpty()) {
            injectSwimlane(model, canvas);
        }
    }

    private void restoreDiagramElementIds(BpmnModelInstance model, BpmnOriginalCanvas snapshot) {
        final String originalDiagramId = snapshot.getOriginalDiagramId();
        if (originalDiagramId != null && !originalDiagramId.isBlank()) {
            model.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnDiagram.class)
                    .stream().findFirst()
                    .ifPresent(d -> d.getDomElement().setAttribute(ID, originalDiagramId));
        }

        // restore original shape id from the canvas for each known element.
        for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
            BaseElement el = shape.getBpmnElement();
            if (el == null) continue;
            final String origShapeId = snapshot.shapeIdFor(el.getId());
            if (origShapeId != null && !origShapeId.isBlank()) {
                shape.getDomElement().setAttribute(ID, origShapeId);
            }
        }

        // restore original edge id from the canvas for each known src→tgt pair.
        for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
            BaseElement el = edge.getBpmnElement();
            if (!(el instanceof SequenceFlow sf)) continue;
            FlowNode src = sf.getSource();
            FlowNode tgt = sf.getTarget();
            if (src == null || tgt == null) continue;
            final String origEdgeId = snapshot.edgeIdFor(src.getId(), tgt.getId());
            if (origEdgeId != null && !origEdgeId.isBlank()) {
                edge.getDomElement().setAttribute(ID, origEdgeId);
            }
        }
    }

    private void injectSwimlane(BpmnModelInstance model, BpmnOriginalCanvas canvas) {
        final var processes = model.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class);
        if (processes.isEmpty()) return;
        final org.camunda.bpm.model.bpmn.instance.Process process = processes.iterator().next();

        Collaboration collaboration = model.getModelElementsByType(Collaboration.class).stream().findFirst().orElse(null);
        if (collaboration == null) {
            collaboration = model.newInstance(Collaboration.class);
            String collabId = canvas.getParticipantShapes().stream()
                    .map(BpmnOriginalCanvas.ParticipantShape::collaborationId)
                    .filter(id -> id != null && !id.isBlank())
                    .findFirst()
                    .orElse("Collaboration_generated");
            collaboration.setId(collabId);
            model.getDefinitions().addChildElement(collaboration);
        }

        // The BPMNPlane must reference the `Collaboration` (not the Process) for a swimlane diagram.
        final Collaboration finalCollab = collaboration;
        final org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane plane =
                model.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane.class)
                        .stream().findFirst().orElse(null);
        if (plane != null) {
            plane.setBpmnElement(finalCollab);
            final String originalPlaneId = canvas.getOriginalPlaneId();
            if (originalPlaneId != null && !originalPlaneId.isBlank()) {
                plane.getDomElement().setAttribute(ID, originalPlaneId);
            }
        }

        for (BpmnOriginalCanvas.ParticipantShape ps : canvas.getParticipantShapes()) {
            final Participant participant = model.newInstance(Participant.class);
            participant.setId(ps.participantId());
            if (ps.participantName() != null && !ps.participantName().isBlank()) {
                participant.setName(ps.participantName());
            }
            participant.setProcess(process);
            collaboration.addChildElement(participant);

            // Add BPMNShape for the swimlane participant
            if (plane != null) {
                final BpmnShape participantShape = model.newInstance(BpmnShape.class);
                participantShape.setId(ps.participantId() + "_di");
                participantShape.setBpmnElement(participant);
                participantShape.setHorizontal(ps.isHorizontal());

                final Bounds pb = model.newInstance(Bounds.class);
                pb.setX(ps.bounds().x());
                pb.setY(ps.bounds().y());
                pb.setWidth(ps.bounds().w());
                pb.setHeight(ps.bounds().h());
                participantShape.setBounds(pb);
                plane.addChildElement(participantShape);

                final var participantLanes = canvas.getLaneShapes().stream()
                        .filter(ls -> ps.participantId().equals(ls.participantId()))
                        .toList();

                if (!participantLanes.isEmpty()) {
                    final org.camunda.bpm.model.bpmn.instance.LaneSet laneSet =
                            model.newInstance(org.camunda.bpm.model.bpmn.instance.LaneSet.class);
                    process.getLaneSets().add(laneSet);

                    for (BpmnOriginalCanvas.LaneShape ls : participantLanes) {
                        final Lane lane = model.newInstance(Lane.class);
                        lane.setId(ls.laneId());

                        if (ls.laneName() != null && !ls.laneName().isBlank()) {
                            lane.setName(ls.laneName());
                        }

                        laneSet.getLanes().add(lane);

                        final BpmnShape laneShape = model.newInstance(BpmnShape.class);
                        laneShape.setId(ls.laneId() + "_di");
                        laneShape.setBpmnElement(lane);
                        laneShape.setHorizontal(ls.isHorizontal());

                        final Bounds lb = model.newInstance(Bounds.class);
                        lb.setX(ls.bounds().x());
                        lb.setY(ls.bounds().y());
                        lb.setWidth(ls.bounds().w());
                        lb.setHeight(ls.bounds().h());
                        laneShape.setBounds(lb);

                        plane.addChildElement(laneShape);
                    }
                }
            }
        }
    }

    private BpmnDiagramLayoutOptimizer.ScaleResult scaleAndDistributeIncrementally(BpmnDiagramLayoutOptimizer bpmnDiagramLayoutOptimizer, BpmnModelInstance model, double multiplier, BpmnOriginalCanvas canvas) {
        final Map<String, Bounds> boundsById = new LinkedHashMap<>();
        final Map<String, List<BpmnShape>> boundaryEventsByParent = new LinkedHashMap<>();

        for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
            Bounds bounds = shape.getBounds();
            if (bounds == null) continue;

            BaseElement element = shape.getBpmnElement();
            if (element instanceof BoundaryEvent boundaryEvent) {
                BpmnOriginalCanvas.Rectangle rectangle = canvas.boundsOf(boundaryEvent.getId());
                if (rectangle != null) {
                    bounds.setX(rectangle.x());
                    bounds.setY(rectangle.y());
                } else {
                    Activity parent = boundaryEvent.getAttachedTo();
                    if (parent != null) {
                        boundaryEventsByParent
                                .computeIfAbsent(parent.getId(), k -> new ArrayList<>())
                                .add(shape);
                    }
                }
            } else {
                BpmnOriginalCanvas.Rectangle rectangle = (element != null) ? canvas.boundsOf(element.getId()) : null;
                if (rectangle != null) {
                    //  restore exact canvas coordinates
                    bounds.setX(rectangle.x());
                    bounds.setY(rectangle.y());
                    if (element instanceof SubProcess) {
                        bounds.setWidth(rectangle.w());
                        bounds.setHeight(rectangle.h());
                    }
                } else {
                    // New node: apply multiplier (same as applySpacingMultiplier)
                    bounds.setX(bounds.getX() * multiplier);
                    bounds.setY(bounds.getY() * multiplier);
                    if (element instanceof SubProcess) {
                        bounds.setWidth(bounds.getWidth() * multiplier);
                        bounds.setHeight(bounds.getHeight() * multiplier);
                    }
                }
            }
            if (element != null) boundsById.put(element.getId(), bounds);
        }

        addNewNodePositions(model, boundsById, canvas);
        bpmnDiagramLayoutOptimizer.distributeBoundaryEvents(boundaryEventsByParent, boundsById);

        final List<BpmnEdge> routableEdges = new ArrayList<>();
        for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
            BaseElement element = edge.getBpmnElement();
            if (element instanceof SequenceFlow sf) {
                FlowNode src = sf.getSource();
                FlowNode tgt = sf.getTarget();
                boolean hasBounds = src != null && tgt != null && boundsById.containsKey(src.getId()) && boundsById.containsKey(tgt.getId());
                if (hasBounds && edge.getWaypoints().size() >= 2) {
                    routableEdges.add(edge);
                } else {
                    bpmnDiagramLayoutOptimizer.scaleWaypoints(edge, multiplier);
                }
            } else {
                bpmnDiagramLayoutOptimizer.scaleWaypoints(edge, multiplier);
            }
        }

        return new BpmnDiagramLayoutOptimizer.ScaleResult(boundsById, routableEdges);
    }

    private void addNewNodePositions(BpmnModelInstance model, Map<String, Bounds> boundsById, BpmnOriginalCanvas canvas) {
        final double SEED_PADDING = 50.0;

        for (FlowNode node : model.getModelElementsByType(FlowNode.class)) {
            if (canvas.hasNode(node.getId())) continue;

            Bounds nodeBounds = boundsById.get(node.getId());
            if (nodeBounds == null) continue;

            Bounds pinnedNeighbor = null;
            for (SequenceFlow sf : node.getIncoming()) {
                FlowNode src = sf.getSource();
                if (src != null && canvas.hasNode(src.getId())) {
                    pinnedNeighbor = boundsById.get(src.getId());
                    break;
                }
            }
            if (pinnedNeighbor == null) {
                for (SequenceFlow sf : node.getOutgoing()) {
                    FlowNode tgt = sf.getTarget();
                    if (tgt != null && canvas.hasNode(tgt.getId())) {
                        pinnedNeighbor = boundsById.get(tgt.getId());
                        break;
                    }
                }
            }

            if (pinnedNeighbor != null) {
                nodeBounds.setX(pinnedNeighbor.getX() + pinnedNeighbor.getWidth() + SEED_PADDING);
                nodeBounds.setY(pinnedNeighbor.getY() + (pinnedNeighbor.getHeight() - nodeBounds.getHeight()) / 2.0);
            }
        }
    }

    private void resolveShapeOverlapsIncrementally(Map<String, Bounds> boundsById, BpmnModelInstance model, Set<String> pinnedIds) {
        final double OVERLAP_PADDING = 30.0;

        final Set<String> immovableIds = new HashSet<>(pinnedIds);
        for (BoundaryEvent be : model.getModelElementsByType(BoundaryEvent.class)) {
            immovableIds.add(be.getId());
        }
        for (SubProcess sp : model.getModelElementsByType(SubProcess.class)) {
            immovableIds.add(sp.getId());
            for (FlowElement child : sp.getFlowElements()) {
                immovableIds.add(child.getId());
            }
        }

        final List<Map.Entry<String, Bounds>> moveable = boundsById.entrySet().stream()
                .filter(e -> !immovableIds.contains(e.getKey()))
                .sorted(Comparator.comparingDouble((Map.Entry<String, Bounds> e) -> e.getValue().getY())
                        .thenComparingDouble(e -> e.getValue().getX()))
                .toList();

        boolean changed = true;
        for (int i = 0; changed && i < 20; i++) {
            changed = false;
            for (Map.Entry<String, Bounds> nodeBound : moveable) {
                Bounds a = nodeBound.getValue();
                String aId = nodeBound.getKey();
                for (Map.Entry<String, Bounds> other : boundsById.entrySet()) {
                    if (other.getKey().equals(aId)) continue;
                    Bounds b = other.getValue();
                    if (shapesOverlap(a, b)) {
                        a.setY(b.getY() + b.getHeight() + OVERLAP_PADDING);
                        changed = true;
                    }
                }
            }
        }
    }

    private void alignAndRouteIncrementally(BpmnDiagramLayoutOptimizer bpmnDiagramLayoutOptimizer, BpmnModelInstance model, Map<String, Bounds> boundsById, List<BpmnEdge> edges, double multiplier, Set<String> pinnedIds, BpmnOriginalCanvas canvas) {
        bpmnDiagramLayoutOptimizer.alignCenters(model, boundsById, edges, pinnedIds);

        final List<BpmnDiagramLayoutOptimizer.EdgeRoute> routes = new ArrayList<>();

        for (BpmnEdge edge : edges) {
            SequenceFlow sf = (SequenceFlow) edge.getBpmnElement();
            FlowNode src = sf.getSource();
            FlowNode tgt = sf.getTarget();
            Bounds srcB = boundsById.get(src.getId());
            Bounds tgtB = boundsById.get(tgt.getId());

            if (srcB == null || tgtB == null) {
                bpmnDiagramLayoutOptimizer.scaleWaypoints(edge, multiplier);
                continue;
            }

            boolean bothPinned = pinnedIds.contains(src.getId()) && pinnedIds.contains(tgt.getId());
            List<BpmnOriginalCanvas.Pointer> canvasPointers = canvas.waypointsFor(src.getId(), tgt.getId());

            if (bothPinned && canvasPointers != null) {
                restoreWaypoints(bpmnDiagramLayoutOptimizer, model, edge, canvasPointers);
            } else {
                BpmnDiagramLayoutOptimizer.Side[] sides = bpmnDiagramLayoutOptimizer.computeSides(src, srcB, tgt, tgtB, boundsById);
                routes.add(new BpmnDiagramLayoutOptimizer.EdgeRoute(edge, src, tgt, srcB, tgtB, sides[0], sides[1]));
            }
        }

        final Map<BpmnEdge, double[]> entryPoints = bpmnDiagramLayoutOptimizer.computeEntryPoints(routes);
        final Map<BpmnEdge, double[]> exitPoints = bpmnDiagramLayoutOptimizer.computeExitPoints(routes, bpmnDiagramLayoutOptimizer.computeContestedExitKeys(routes));

        for (BpmnDiagramLayoutOptimizer.EdgeRoute r : routes) {
            double[] s = exitPoints.get(r.edge());
            double[] t = entryPoints.get(r.edge());
            bpmnDiagramLayoutOptimizer.routeOrthogonal(model, r.edge(), s, r.exitSide(), t, r.entrySide());
        }
    }

    private void restoreWaypoints(BpmnDiagramLayoutOptimizer bpmnDiagramLayoutOptimizer, BpmnModelInstance model, BpmnEdge edge, List<BpmnOriginalCanvas.Pointer> points) {
        new ArrayList<>(edge.getWaypoints()).forEach(edge::removeChildElement);
        for (BpmnOriginalCanvas.Pointer pt : points) {
            bpmnDiagramLayoutOptimizer.addWaypoint(model, edge, pt.x(), pt.y());
        }
    }

    private void addNode(Map<String, Bounds> boundsById, BpmnModelInstance model) {
        final double MIN_SPACING = 20.0;

        final Set<String> insideSubprocess = new HashSet<>();
        for (SubProcess sp : model.getModelElementsByType(SubProcess.class)) {
            for (FlowElement fe : sp.getFlowElements()) {
                insideSubprocess.add(fe.getId());
            }
        }

        final List<Bounds> rootBounds = new ArrayList<>();
        final List<SequenceFlow> rootFlows = new ArrayList<>();
        for (FlowNode fn : model.getModelElementsByType(FlowNode.class)) {
            if (insideSubprocess.contains(fn.getId()) || fn instanceof BoundaryEvent) continue;
            Bounds b = boundsById.get(fn.getId());
            if (b != null) {
                rootBounds.add(b);
                for (SequenceFlow sf : fn.getOutgoing()) {
                    FlowNode tgt = sf.getTarget();
                    if (tgt != null && !insideSubprocess.contains(tgt.getId()) && !(tgt instanceof BoundaryEvent)) {
                        rootFlows.add(sf);
                    }
                }
            }
        }
        applyPushRight(rootBounds, MIN_SPACING);
        addNodesInSequence(rootFlows, boundsById, MIN_SPACING);

        for (SubProcess sp : model.getModelElementsByType(SubProcess.class)) {
            final List<Bounds> children = new ArrayList<>();
            final List<SequenceFlow> childFlows = new ArrayList<>();
            for (FlowElement fe : sp.getFlowElements()) {
                if (fe instanceof FlowNode fn && !(fn instanceof BoundaryEvent)) {
                    Bounds b = boundsById.get(fn.getId());
                    if (b != null) {
                        children.add(b);
                        childFlows.addAll(fn.getOutgoing());
                    }
                }
            }
            applyPushRight(children, MIN_SPACING);
            addNodesInSequence(childFlows, boundsById, MIN_SPACING);
        }
    }

    private static void applyPushRight(List<Bounds> bounds, double minSpacing) {
        if (bounds.size() < 2) return;
        bounds.sort(Comparator.comparingDouble(Bounds::getX));
        for (int i = 1; i < bounds.size(); i++) {
            Bounds prev = bounds.get(i - 1);
            Bounds curr = bounds.get(i);
            boolean yOverlap = prev.getY() < curr.getY() + curr.getHeight()  && curr.getY() < prev.getY() + prev.getHeight();
            if (!yOverlap) continue;
            double required = prev.getX() + prev.getWidth() + minSpacing;
            if (curr.getX() < required) curr.setX(required);
        }
    }

    private static void addNodesInSequence(Collection<SequenceFlow> flows,  Map<String, Bounds> boundsById,  double minSpacing) {
        if (flows.isEmpty()) return;
        boolean changed = true;
        for (int iter = 0; changed && iter < 50; iter++) {
            changed = false;
            for (SequenceFlow sf : flows) {
                FlowNode src = sf.getSource();
                FlowNode tgt = sf.getTarget();
                if (src == null || tgt == null) continue;
                Bounds srcB = boundsById.get(src.getId());
                Bounds tgtB = boundsById.get(tgt.getId());
                if (srcB == null || tgtB == null) continue;
                boolean yOverlap = srcB.getY() < tgtB.getY() + tgtB.getHeight() && tgtB.getY() < srcB.getY() + srcB.getHeight();
                if (!yOverlap) continue;
                double required = srcB.getX() + srcB.getWidth() + minSpacing;
                if (tgtB.getX() < required) {
                    tgtB.setX(required);
                    changed = true;
                }
            }
        }
    }
}
