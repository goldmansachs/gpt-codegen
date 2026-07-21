package org.rj.modelgen.bpmn.generation.render;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Lane;
import org.camunda.bpm.model.bpmn.instance.Participant;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnDiagram;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;

import java.util.*;

public class BpmnOriginalCanvas {

    public record Rectangle(double x, double y, double w, double h) {}
    public record Pointer(double x, double y) {}

    // swimlane shape from the original canvas
    public record ParticipantShape(String participantId, String participantName, String collaborationId, Rectangle bounds, boolean isHorizontal) {}
    public record LaneShape(String laneId, String laneName, String participantId, Rectangle bounds, boolean isHorizontal) {}

    private final Map<String, Rectangle> nodeBounds;
    private final Map<String, List<Pointer>> waypointsBySrcTgt;
    private final Set<String> containerIds;
    private final List<ParticipantShape> participantShapes;
    private final List<LaneShape> laneShapes;
    private final String originalPlaneId;
    private final String originalDiagramId;
    private final Map<String, String> elementToShapeId;
    private final Map<String, String> edgeKeyToEdgeId;

    private BpmnOriginalCanvas(Map<String, Rectangle> nodeBounds, Map<String, List<Pointer>> waypointsBySrcTgt, Set<String> containerIds,
                               List<ParticipantShape> participantShapes, List<LaneShape> laneShapes, String originalPlaneId,
                               String originalDiagramId, Map<String, String> elementToShapeId, Map<String, String> edgeKeyToEdgeId) {
        this.nodeBounds = nodeBounds;
        this.waypointsBySrcTgt = waypointsBySrcTgt;
        this.containerIds = containerIds;
        this.participantShapes = participantShapes;
        this.laneShapes = laneShapes;
        this.originalPlaneId = originalPlaneId;
        this.originalDiagramId = originalDiagramId;
        this.elementToShapeId = elementToShapeId;
        this.edgeKeyToEdgeId = edgeKeyToEdgeId;
    }

    public static BpmnOriginalCanvas capture(BpmnModelInstance canvas) {
        if (canvas == null) {
            return new BpmnOriginalCanvas(Map.of(), Map.of(), Set.of(), List.of(), List.of(), null, null, Map.of(), Map.of());
        }

        final Map<String, Rectangle> nodeBounds = new LinkedHashMap<>();
        final Set<String> containerIds = new LinkedHashSet<>();
        final List<ParticipantShape> participantShapes = new ArrayList<>();
        final List<LaneShape> laneShapes = new ArrayList<>();
        final Map<String, String> elementToShapeId = new LinkedHashMap<>();
        final Map<String, String> edgeKeyToEdgeId = new LinkedHashMap<>();

        final String originalPlaneId = canvas.getModelElementsByType(BpmnPlane.class)
                .stream()
                .findFirst()
                .map(p -> p.getId())
                .orElse(null);

        final String originalDiagramId = canvas.getModelElementsByType(BpmnDiagram.class)
                .stream()
                .findFirst()
                .map(d -> d.getId())
                .orElse(null);

        //  swimlane shapes
        final Map<String, String> participantToCollaboration = new LinkedHashMap<>();
        for (var collab : canvas.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Collaboration.class)) {
            for (var p : collab.getParticipants()) {
                participantToCollaboration.put(p.getId(), collab.getId());
            }
        }

        final Map<String, String> laneToParticipant = new LinkedHashMap<>();
        for (var participant : canvas.getModelElementsByType(Participant.class)) {
            if (participant.getProcess() == null) continue;
            for (var laneSet : participant.getProcess().getLaneSets()) {
                for (var lane : laneSet.getLanes()) {
                    laneToParticipant.put(lane.getId(), participant.getId());
                }
            }
        }

        for (BpmnShape shape : canvas.getModelElementsByType(BpmnShape.class)) {
            Bounds b = shape.getBounds();
            if (b == null) continue;

            BaseElement el = shape.getBpmnElement();
            if (el == null) continue;

            if (el instanceof Participant p) {
                String collabId = participantToCollaboration.get(p.getId());
                participantShapes.add(new ParticipantShape(p.getId(), p.getName(), collabId, new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight()), shape.isHorizontal()));
                continue;
            }

            if (el instanceof Lane lane) {
                String parentParticipantId = laneToParticipant.get(lane.getId());
                laneShapes.add(new LaneShape(lane.getId(), lane.getName(), parentParticipantId, new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight()), shape.isHorizontal()));
                continue;
            }

            nodeBounds.put(el.getId(), new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
            if (el instanceof SubProcess) {
                containerIds.add(el.getId());
            }

            if (shape.getId() != null) {
                elementToShapeId.put(el.getId(), shape.getId());
            }
        }

        for (BpmnEdge edge : canvas.getModelElementsByType(BpmnEdge.class)) {
            BaseElement el = edge.getBpmnElement();
            if (!(el instanceof SequenceFlow sf)) continue;
            FlowNode src = sf.getSource();
            FlowNode tgt = sf.getTarget();
            if (src != null && tgt != null && edge.getId() != null) {
                edgeKeyToEdgeId.put(edgeKey(src.getId(), tgt.getId()), edge.getId());
            }
        }

        final Map<String, List<Pointer>> waypointsBySrcTgt = new LinkedHashMap<>();

        for (BpmnEdge edge : canvas.getModelElementsByType(BpmnEdge.class)) {
            BaseElement el = edge.getBpmnElement();
            if (!(el instanceof SequenceFlow sf)) continue;

            FlowNode src = sf.getSource();
            FlowNode tgt = sf.getTarget();
            if (src == null || tgt == null) continue;

            final List<Pointer> pts = new ArrayList<>();
            for (Waypoint wp : edge.getWaypoints()) {
                pts.add(new Pointer(wp.getX(), wp.getY()));
            }

            if (!pts.isEmpty()) {
                waypointsBySrcTgt.put(edgeKey(src.getId(), tgt.getId()), pts);
            }
        }

        return new BpmnOriginalCanvas(Collections.unmodifiableMap(nodeBounds), Collections.unmodifiableMap(waypointsBySrcTgt), Collections.unmodifiableSet(containerIds),
                Collections.unmodifiableList(participantShapes), Collections.unmodifiableList(laneShapes), originalPlaneId, originalDiagramId,
                Collections.unmodifiableMap(elementToShapeId), Collections.unmodifiableMap(edgeKeyToEdgeId));
    }

    public boolean hasNode(String id) {
        return id != null && nodeBounds.containsKey(id);
    }

    public Rectangle boundsOf(String id) {
        return id == null ? null : nodeBounds.get(id);
    }

    public List<Pointer> waypointsFor(String srcId, String tgtId) {
        return waypointsBySrcTgt.get(edgeKey(srcId, tgtId));
    }

    public List<ParticipantShape> getParticipantShapes() {
        return participantShapes;
    }

    public List<LaneShape> getLaneShapes() {
        return laneShapes;
    }

    public String getOriginalPlaneId() {
        return originalPlaneId;
    }

    public String getOriginalDiagramId() {
        return originalDiagramId;
    }

    public String shapeIdFor(String elementId) {
        return elementId == null ? null : elementToShapeId.get(elementId);
    }

    public String edgeIdFor(String srcId, String tgtId) {
        return edgeKeyToEdgeId.get(edgeKey(srcId, tgtId));
    }

    public boolean isEmpty() {
        return nodeBounds.isEmpty() && participantShapes.isEmpty();
    }

    private static String edgeKey(String srcId, String tgtId) {
        return srcId + "->" + tgtId;
    }
}
