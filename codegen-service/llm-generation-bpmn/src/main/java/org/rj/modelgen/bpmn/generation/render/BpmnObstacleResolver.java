package org.rj.modelgen.bpmn.generation.render;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;

import java.util.*;

public class BpmnObstacleResolver {

    private static final int MAX_REROUTE_ITERS = 10;
    private static final double PADDING = 30.0;
    private static final double INSET = 0.5;

    public void resolveConnectorObstacles(BpmnModelInstance model, BpmnDiagramLayoutOptimizer.LayoutScope root, Map<String, Bounds> boundsById) {
        List<BpmnDiagramLayoutOptimizer.LayoutScope> allScopes = new ArrayList<>();
        collectScopes(root, allScopes);
        for (var scope : allScopes) {
            resolveForScope(model, scope, boundsById);
        }
    }

    private void collectScopes(BpmnDiagramLayoutOptimizer.LayoutScope scope, List<BpmnDiagramLayoutOptimizer.LayoutScope> result) {
        result.add(scope);
        for (var child : scope.children()) collectScopes(child, result);
    }

    private void resolveForScope(BpmnModelInstance model, BpmnDiagramLayoutOptimizer.LayoutScope scope, Map<String, Bounds> boundsById) {
        Map<String, Bounds> scopeBounds = new LinkedHashMap<>();
        for (String id : scope.nodeIds()) {
            Bounds bounds = boundsById.get(id);
            if (bounds != null) scopeBounds.put(id, bounds);
        }

        if (scopeBounds.isEmpty() || scope.edges().isEmpty()) return;

        // fix overlapping connectors and nodes iteratively
        for (int iter = 0; iter < MAX_REROUTE_ITERS; iter++) {
            boolean changed = false;
            for (BpmnEdge edge : scope.edges()) {
                if (fixEdgeCollisions(model, edge, scopeBounds)) changed = true;
            }
            if (!changed) {
                break;
            }
        }
    }

    private boolean fixEdgeCollisions(BpmnModelInstance model, BpmnEdge edge, Map<String, Bounds> scopeBounds) {
        if (!(edge.getBpmnElement() instanceof SequenceFlow sequenceFlow)) return false;

        FlowNode src = sequenceFlow.getSource();
        FlowNode tgt = sequenceFlow.getTarget();

        Map<String, Bounds> obstacles = new LinkedHashMap<>(scopeBounds);
        if (src != null) obstacles.remove(src.getId());
        if (tgt != null) obstacles.remove(tgt.getId());
        if (obstacles.isEmpty()) return false;

        List<Waypoint> waypoints = new ArrayList<>(edge.getWaypoints());
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Waypoint a = waypoints.get(i);
            Waypoint b = waypoints.get(i + 1);
            Bounds hit = firstObstacleHit(a.getX(), a.getY(), b.getX(), b.getY(), obstacles);
            if (hit != null) {
                boolean isHorizontal = Math.abs(b.getY() - a.getY()) <= 1.0;
                rerouteSegment(model, edge, waypoints, i, isHorizontal, hit, obstacles);
                return true;
            }
        }
        return false;
    }

    private void shiftHorizontalSequenceOfNodes(BpmnModelInstance model, BpmnEdge edge, List<Waypoint> waypoints, double blockedY, double freeY) {
        final int last = waypoints.size() - 1;
        final boolean firstNode = Math.abs(waypoints.get(0).getY() - blockedY) <= 1.0;
        final boolean lastNode = Math.abs(waypoints.get(last).getY() - blockedY) <= 1.0;

        // arrow direction: positive = going right, negative = going left.
        final double srcX = waypoints.get(0).getX();
        final double tgtX = waypoints.get(last).getX();
        final double direction = (tgtX >= srcX) ? 1.0 : -1.0;

        final List<double[]> pointers = new ArrayList<>();
        for (int i = 0; i <= last; i++) {
            Waypoint waypoint = waypoints.get(i);
            if (i == 0) {
                pointers.add(new double[]{waypoint.getX(), waypoint.getY()}); // always keep source anchor
                if (firstNode) {
                    double stubX = waypoint.getX() + direction * PADDING;
                    pointers.add(new double[]{stubX, waypoint.getY()});
                    pointers.add(new double[]{stubX, freeY});
                }
            } else if (i == last) {
                if (lastNode) {
                    double stubX = waypoint.getX() - direction * PADDING;
                    pointers.add(new double[]{stubX, freeY});
                    pointers.add(new double[]{stubX, waypoint.getY()});
                }
                pointers.add(new double[]{waypoint.getX(), waypoint.getY()}); // always keep target anchor
            } else if (Math.abs(waypoint.getY() - blockedY) <= 1.0) {
                pointers.add(new double[]{waypoint.getX(), freeY}); // shift intermediate nodes in the sequence
            } else {
                pointers.add(new double[]{waypoint.getX(), waypoint.getY()});
            }
        }
        applyWaypoints(model, edge, pointers);
    }

    private Bounds firstObstacleHit(double x0, double y0, double x1, double y1, Map<String, Bounds> obstacles) {
        boolean horizontal = Math.abs(y1 - y0) <= 1.0;
        boolean vertical = Math.abs(x1 - x0) <= 1.0;

        for (Bounds bounds : obstacles.values()) {
            if (horizontal) {
                double y = (y0 + y1) / 2.0;
                double minX = Math.min(x0, x1);
                double maxX = Math.max(x0, x1);
                if (y > bounds.getY() + INSET
                    && y < bounds.getY() + bounds.getHeight() - INSET
                    && maxX > bounds.getX() + INSET
                    && minX < bounds.getX() + bounds.getWidth() - INSET) {
                      return bounds;
                }
            } else if (vertical) {
                double x = (x0 + x1) / 2.0;
                double minY = Math.min(y0, y1);
                double maxY = Math.max(y0, y1);
                if (x > bounds.getX() + INSET
                    && x < bounds.getX() + bounds.getWidth() - INSET
                    && maxY > bounds.getY() + INSET
                    && minY < bounds.getY() + bounds.getHeight() - INSET) {
                      return bounds;
                }
            } else {
                double minX = Math.min(x0, x1); double maxX = Math.max(x0, x1);
                double minY = Math.min(y0, y1); double maxY = Math.max(y0, y1);
                if (maxX > bounds.getX() + INSET && minX < bounds.getX() + bounds.getWidth() - INSET && maxY > bounds.getY() + INSET && minY < bounds.getY() + bounds.getHeight() - INSET) {
                    return bounds;
                }
            }
        }
        return null;
    }

    private void rerouteSegment(BpmnModelInstance model, BpmnEdge edge, List<Waypoint> waypoints, int segmentStart, boolean isHorizontal, Bounds obstacle, Map<String, Bounds> obstacles) {
        if (isHorizontal) {
            double blockedY = waypoints.get(segmentStart).getY();
            double x0 = waypoints.get(segmentStart).getX();
            double x1 = waypoints.get(segmentStart + 1).getX();
            double freeY = findFreeHorizontalY(blockedY, x0, x1, obstacle, obstacles);
            if (!Double.isNaN(freeY)) {
                shiftHorizontalSequenceOfNodes(model, edge, waypoints, blockedY, freeY);
            } else {
                insertCShapeHorizontally(model, edge, waypoints, segmentStart, obstacle);
            }
        } else {
            double blockedX = waypoints.get(segmentStart).getX();
            double y0 = waypoints.get(segmentStart).getY();
            double y1 = waypoints.get(segmentStart + 1).getY();
            double freeX = findFreeVerticalX(blockedX, y0, y1, obstacle, obstacles);
            if (!Double.isNaN(freeX)) {
                shiftVerticalSequenceOfNodes(model, edge, waypoints, blockedX, freeX);
            } else {
                insertCShapeVertically(model, edge, waypoints, segmentStart, obstacle);
            }
        }
    }

    private void shiftVerticalSequenceOfNodes(BpmnModelInstance model, BpmnEdge edge, List<Waypoint> waypoints, double blockedX, double freeX) {
        final int last = waypoints.size() - 1;
        final boolean firstNode = Math.abs(waypoints.get(0).getX() - blockedX) <= 1.0;
        final boolean lastNode = Math.abs(waypoints.get(last).getX() - blockedX) <= 1.0;

        final List<double[]> pts = new ArrayList<>();
        for (int i = 0; i <= last; i++) {
            Waypoint waypoint = waypoints.get(i);
            if (i == 0) {
                pts.add(new double[]{waypoint.getX(), waypoint.getY()});
                if (firstNode) {
                    pts.add(new double[]{freeX, waypoint.getY()});
                }
            } else if (i == last) {
                if (lastNode) {
                    pts.add(new double[]{freeX, waypoint.getY()});
                }
                pts.add(new double[]{waypoint.getX(), waypoint.getY()});
            } else if (Math.abs(waypoint.getX() - blockedX) <= 1.0) {
                pts.add(new double[]{freeX, waypoint.getY()});
            } else {
                pts.add(new double[]{waypoint.getX(), waypoint.getY()});
            }
        }
        applyWaypoints(model, edge, pts);
    }

    private void applyWaypoints(BpmnModelInstance model, BpmnEdge edge, List<double[]> pointers) {
        new ArrayList<>(edge.getWaypoints()).forEach(edge::removeChildElement);
        for (double[] pt : pointers) addWaypoint(model, edge, pt[0], pt[1]);
    }

    private double findFreeHorizontalY(double blockedY, double x0, double x1,
                                       Bounds obstacle, Map<String, Bounds> obstacles) {
        double above = obstacle.getY() - PADDING;
        double below = obstacle.getY() + obstacle.getHeight() + PADDING;
        double[] candidates = getClosestNode(above, below, blockedY);
        for (double y : candidates) {
            if (!horizontalSegmentObstacles(y, x0, x1, obstacles)) return y;
        }
        return Double.NaN;
    }

    private double findFreeVerticalX(double blockedX, double y0, double y1, Bounds obstacle, Map<String, Bounds> obstacles) {
        double left  = obstacle.getX() - PADDING;
        double right = obstacle.getX() + obstacle.getWidth() + PADDING;
        double[] candidates = getClosestNode(left, right, blockedX);
        for (double x : candidates) {
            if (!verticalSegmentObstacles(x, y0, y1, obstacles)) return x;
        }
        return Double.NaN;
    }

    private boolean horizontalSegmentObstacles(double y, double x0, double x1, Map<String, Bounds> obstacles) {
        double minX = Math.min(x0, x1);
        double maxX = Math.max(x0, x1);
        for (Bounds bounds : obstacles.values()) {
            if (y > bounds.getY() + INSET && y < bounds.getY() + bounds.getHeight() - INSET && maxX > bounds.getX() + INSET && minX < bounds.getX() + bounds.getWidth()  - INSET) {
                return true;
            }
            if (y > bounds.getY() + INSET && y < bounds.getY() + bounds.getHeight() - INSET) {
                if (maxX > bounds.getX() - 2.0 * PADDING && maxX <= bounds.getX()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean verticalSegmentObstacles(double x, double y0, double y1, Map<String, Bounds> obstacles) {
        double minY = Math.min(y0, y1);
        double maxY = Math.max(y0, y1);
        for (Bounds bounds : obstacles.values()) {
            if (x > bounds.getX() + INSET && x < bounds.getX() + bounds.getWidth() - INSET && maxY > bounds.getY() + INSET && minY < bounds.getY() + bounds.getHeight() - INSET) {
                return true;
            }
        }
        return false;
    }

    private static double[] getClosestNode(double a, double b, double reference) {
        return Math.abs(a - reference) <= Math.abs(b - reference) ? new double[]{a, b} : new double[]{b, a};
    }

    private void insertCShapeHorizontally(BpmnModelInstance model, BpmnEdge edge, List<Waypoint> waypoints, int segmentStart, Bounds obstacle) {
        double x0 = waypoints.get(segmentStart).getX();
        double x1 = waypoints.get(segmentStart + 1).getX();
        double y = waypoints.get(segmentStart).getY();

        // route around blocked node
        double detourY = (y >= BpmnDiagramLayoutOptimizer.centerY(obstacle)) ? obstacle.getY() + obstacle.getHeight() + PADDING : obstacle.getY() - PADDING;

        rebuildWithDetour(model, edge, waypoints, segmentStart, segmentStart + 1, new double[][]{{x0, detourY}, {x1, detourY}});
    }

    private void insertCShapeVertically(BpmnModelInstance model, BpmnEdge edge, List<Waypoint> waypoints, int segmentStart, Bounds obstacle) {
        double x = waypoints.get(segmentStart).getX();
        double y0 = waypoints.get(segmentStart).getY();
        double y1 = waypoints.get(segmentStart + 1).getY();

        // route around blocked node
        double detourX = (x >= BpmnDiagramLayoutOptimizer.centerX(obstacle)) ? obstacle.getX() + obstacle.getWidth() + PADDING : obstacle.getX() - PADDING;

        rebuildWithDetour(model, edge, waypoints, segmentStart, segmentStart + 1, new double[][]{{detourX, y0}, {detourX, y1}});
    }

    private void rebuildWithDetour(BpmnModelInstance model, BpmnEdge edge, List<Waypoint> original, int from, int to, double[][] additionalCoords) {
        new ArrayList<>(edge.getWaypoints()).forEach(edge::removeChildElement);
        for (int i = 0; i <= from; i++) {
            addWaypoint(model, edge, original.get(i).getX(), original.get(i).getY());
        }
        for (double[] pt : additionalCoords) {
            addWaypoint(model, edge, pt[0], pt[1]);
        }
        for (int i = to; i < original.size(); i++) {
            addWaypoint(model, edge, original.get(i).getX(), original.get(i).getY());
        }
    }

    private void addWaypoint(BpmnModelInstance model, BpmnEdge edge, double x, double y) {
        Waypoint waypoint = model.newInstance(Waypoint.class);
        waypoint.setX(x);
        waypoint.setY(y);
        edge.addChildElement(waypoint);
    }
}
