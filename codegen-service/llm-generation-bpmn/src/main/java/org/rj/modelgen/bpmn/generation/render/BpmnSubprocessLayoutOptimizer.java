package org.rj.modelgen.bpmn.generation.render;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BpmnSubprocessLayoutOptimizer {

    private static final double INTERNAL_PADDING  = 30;
    private static final double EXTERNAL_PADDING = 50;

    void layout(BpmnModelInstance model, Map<String, Bounds> boundsById) {
        for (SubProcess sp : model.getModelElementsByType(SubProcess.class)) {
            fitAndCenter(model, sp, boundsById);
        }
        resolveExternalOverlaps(model, boundsById);
    }

    public void refit(BpmnModelInstance model, String containerId, Map<String, Bounds> boundsById) {
        for (SubProcess sp : model.getModelElementsByType(SubProcess.class)) {
            if (sp.getId().equals(containerId)) {
                fitAndCenter(model, sp, boundsById);
                resolveExternalOverlaps(model, boundsById);
                return;
            }
        }
    }

    private void fitAndCenter(BpmnModelInstance model, SubProcess sp,
                              Map<String, Bounds> boundsById) {
        Bounds spBounds = boundsById.get(sp.getId());
        if (spBounds == null) return;

        List<Bounds> children = collectChildBounds(sp, boundsById);
        if (children.isEmpty()) return;

        double[] bbox = boundingBox(children);
        double childW = bbox[2] - bbox[0];
        double childH = bbox[3] - bbox[1];

        // Expand container to fit children with padding
        spBounds.setWidth(Math.max(spBounds.getWidth(),   childW + INTERNAL_PADDING * 2));
        spBounds.setHeight(Math.max(spBounds.getHeight(), childH + INTERNAL_PADDING * 2));

        // Calculate centering offset
        double dx = spBounds.getX() + (spBounds.getWidth()  - childW) / 2.0 - bbox[0];
        double dy = spBounds.getY() + (spBounds.getHeight() - childH) / 2.0 - bbox[1];
        if (Math.abs(dx) < 1.0 && Math.abs(dy) < 1.0) return;

        // Apply offset to children, their boundary events, and internal edges
        children.forEach(cb -> { cb.setX(cb.getX() + dx); cb.setY(cb.getY() + dy); });
        shiftChildBoundaryEvents(model, sp, boundsById, dx, dy);
        shiftInternalEdgeWaypoints(model, sp, dx, dy);
    }

    private void resolveExternalOverlaps(BpmnModelInstance model,
                                         Map<String, Bounds> boundsById) {
        for (SubProcess sp : model.getModelElementsByType(SubProcess.class)) {
            Bounds spBounds = boundsById.get(sp.getId());
            if (spBounds == null) continue;
            shiftOverlappingExternalShapes(model, sp, spBounds);
        }
    }

    private void shiftOverlappingExternalShapes(BpmnModelInstance model, SubProcess sp, Bounds spBounds) {
        final double spRight = spBounds.getX() + spBounds.getWidth();
        double leftmostOverlapX = Double.MAX_VALUE;
        final List<Bounds> overlapping = new ArrayList<>();
        for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
            BaseElement el = shape.getBpmnElement();
            if (el == null || belongsToSubProcess(el, sp)) continue;

            Bounds ob = shape.getBounds();
            if (ob != null && boundsOverlap(spBounds, ob)) {
                overlapping.add(ob);
                leftmostOverlapX = Math.min(leftmostOverlapX, ob.getX());
            }
        }
        if (overlapping.isEmpty()) return;

        double shift = spRight + EXTERNAL_PADDING - leftmostOverlapX;
        if (shift <= 0) return;
        for (Bounds ob : overlapping) ob.setX(ob.getX() + shift);
    }

    private static boolean belongsToSubProcess(BaseElement el, SubProcess sp) {
        return el.equals(sp) || isDescendantOf(el, sp)
                || (el instanceof BoundaryEvent be && sp.equals(be.getAttachedTo()));
    }

    private void shiftChildBoundaryEvents(BpmnModelInstance model, SubProcess sp,
                                          Map<String, Bounds> boundsById,
                                          double dx, double dy) {
        for (BoundaryEvent be : model.getModelElementsByType(BoundaryEvent.class)) {
            Activity parent = be.getAttachedTo();
            if (parent != null && isDescendantOf(parent, sp)) {
                Bounds b = boundsById.get(be.getId());
                if (b != null) {
                    b.setX(b.getX() + dx);
                    b.setY(b.getY() + dy);
                }
            }
        }
    }

    private void shiftInternalEdgeWaypoints(BpmnModelInstance model, SubProcess sp,
                                            double dx, double dy) {
        for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
            if (!(edge.getBpmnElement() instanceof SequenceFlow sf)) continue;
            FlowNode src = sf.getSource();
            if (src != null && isDescendantOf(src, sp)) {
                edge.getWaypoints().forEach(wp -> {
                    wp.setX(wp.getX() + dx);
                    wp.setY(wp.getY() + dy);
                });
            }
        }
    }

    private static boolean boundsOverlap(Bounds a, Bounds b) {
        return a.getX() < b.getX() + b.getWidth()
            && a.getX() + a.getWidth()  > b.getX()
            && a.getY() < b.getY() + b.getHeight()
            && a.getY() + a.getHeight() > b.getY();
    }

    private double[] boundingBox(List<Bounds> bounds) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Bounds b : bounds) {
            minX = Math.min(minX, b.getX());
            minY = Math.min(minY, b.getY());
            maxX = Math.max(maxX, b.getX() + b.getWidth());
            maxY = Math.max(maxY, b.getY() + b.getHeight());
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    static boolean isDescendantOf(BaseElement element, SubProcess sp) {
        for (ModelElementInstance p = element.getParentElement(); p != null; p = p.getParentElement()) {
            if (p.equals(sp)) return true;
        }
        return false;
    }

    private List<Bounds> collectChildBounds(SubProcess sp,
                                            Map<String, Bounds> boundsById) {
        List<Bounds> result = new ArrayList<>();
        for (FlowElement child : sp.getFlowElements()) {
            if (child instanceof FlowNode && !(child instanceof BoundaryEvent)) {
                Bounds cb = boundsById.get(child.getId());
                if (cb != null) result.add(cb);
            }
        }
        return result;
    }
}



