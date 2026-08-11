package org.rj.modelgen.bpmn.generation.render;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractActivityBuilder;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmnDiagramLayoutOptimizerTest {

    private final BpmnDiagramLayoutOptimizer optimizer = new BpmnDiagramLayoutOptimizer();

    private static Bounds bounds(BpmnModelInstance model, double x, double y, double w, double h) {
        Bounds b = model.newInstance(Bounds.class);
        b.setX(x);
        b.setY(y);
        b.setWidth(w);
        b.setHeight(h);
        return b;
    }

    private static BpmnModelInstance modelWithTaskInsideSubProcess() {
        return Bpmn.createExecutableProcess("Process_1")
                .startEvent("Start")
                .subProcess("SP")
                .embeddedSubProcess()
                .startEvent("SPStart")
                .endEvent("SPEnd")
                .subProcessDone()
                .userTask("T")
                .endEvent("End")
                .done();
    }

    private static BpmnModelInstance modelWithBoundaryEventOnSubProcess() {
        BpmnModelInstance model = Bpmn.createExecutableProcess("Process_1")
                .startEvent("Start")
                .subProcess("SP")
                .embeddedSubProcess()
                .startEvent("SPStart")
                .endEvent("SPEnd")
                .subProcessDone()
                .endEvent("End")
                .done();
        SubProcess sp = model.getModelElementById("SP");
        ((AbstractActivityBuilder<?, ?>) sp.builder()).boundaryEvent("BE").endEvent("BEEnd").done();
        return model;
    }

    private static BpmnModelInstance modelWithConditionalGateway() {
        return Bpmn.createExecutableProcess("Process_1")
                .startEvent("Start")
                .exclusiveGateway("G")
                .userTask("Down")
                .moveToNode("G")
                .userTask("Right")
                .done();
    }


    @Test
    void selectSide_dominantAxis() {
        BpmnModelInstance model = Bpmn.createEmptyModel();
        Bounds from = bounds(model, 0, 0, 50, 50);
        Bounds to = bounds(model, 155, 169, 100, 80);
        assertEquals(BpmnDiagramLayoutOptimizer.Side.BOTTOM, optimizer.preferredSide(from, to));
    }

    @Test
    void selectSide_xAxis() {
        BpmnModelInstance model = Bpmn.createEmptyModel();
        Bounds from = bounds(model, 0, 0, 50, 50);
        Bounds to = bounds(model, 500, 10, 100, 80);
        assertEquals(BpmnDiagramLayoutOptimizer.Side.RIGHT, optimizer.preferredSide(from, to));
    }

    @Test
    void selectSide_yAxis() {
        BpmnModelInstance model = Bpmn.createEmptyModel();
        Bounds from = bounds(model, 0, 0, 50, 50);
        Bounds to = bounds(model, 10, 500, 100, 80);
        assertEquals(BpmnDiagramLayoutOptimizer.Side.BOTTOM, optimizer.preferredSide(from, to));
    }

    @Test
    void subprocessConnectors() {
        BpmnModelInstance model = modelWithTaskInsideSubProcess();
        SubProcess subProcess = model.getModelElementById("SP");
        Task task = model.getModelElementById("T");

        Map<String, Bounds> boundsById = new HashMap<>();
        Bounds spB = bounds(model, 0, 0, 2000, 200);
        Bounds taskB = bounds(model, 300, 400, 100, 80);
        boundsById.put(subProcess.getId(), spB);
        boundsById.put(task.getId(), taskB);

        BpmnDiagramLayoutOptimizer.Side[] sides = optimizer.computeSides(task, taskB, subProcess, spB, boundsById);
        assertEquals(BpmnDiagramLayoutOptimizer.Side.BOTTOM, sides[1], "entry should be the nearest edge of the rectangle");
        assertEquals(BpmnDiagramLayoutOptimizer.Side.TOP, sides[0], "exit should face the entry side");
    }

    @Test
    void subprocessConnector_entersRightEdge() {
        BpmnModelInstance model = modelWithTaskInsideSubProcess();
        SubProcess subProcess = model.getModelElementById("SP");
        Task task = model.getModelElementById("T");

        Map<String, Bounds> boundsById = new HashMap<>();
        Bounds spB = bounds(model, 0, 0, 500, 400);
        Bounds taskB = bounds(model, 700, 150, 100, 80);
        boundsById.put(subProcess.getId(), spB);
        boundsById.put(task.getId(), taskB);

        BpmnDiagramLayoutOptimizer.Side[] sides = optimizer.computeSides(task, taskB, subProcess, spB, boundsById);
        assertEquals(BpmnDiagramLayoutOptimizer.Side.RIGHT, sides[1]);
        assertEquals(BpmnDiagramLayoutOptimizer.Side.LEFT, sides[0]);
    }

    @Test
    void subprocessWithBoundaryEvents() {
        BpmnModelInstance model = modelWithBoundaryEventOnSubProcess();
        SubProcess subProcess = model.getModelElementById("SP");
        BoundaryEvent be = model.getModelElementById("BE");
        FlowNode target = model.getModelElementById("BEEnd");

        Map<String, Bounds> boundsById = new HashMap<>();
        Bounds spB = bounds(model, 0, 0, 500, 400);
        // Boundary event centred on the subprocess's top edge.
        Bounds beB = bounds(model, 232, -18, 36, 36);
        Bounds targetB = bounds(model, 600, 0, 100, 80);
        boundsById.put(subProcess.getId(), spB);
        boundsById.put(be.getId(), beB);
        boundsById.put(target.getId(), targetB);

        BpmnDiagramLayoutOptimizer.Side[] sides = optimizer.computeSides(be, beB, target, targetB, boundsById);
        assertEquals(BpmnDiagramLayoutOptimizer.Side.TOP, sides[0],
                "a boundary event must exit from the same side it's attached to, not toward the target");
    }

    @Test
    void renderConnectorWithObstacle_LShape() {
        BpmnModelInstance model = modelWithTaskInsideSubProcess();
        BpmnEdge edge = model.getModelElementsByType(BpmnEdge.class).iterator().next();

        double[] s = {0, 0};
        double[] t = {100, 100};
        optimizer.routeOrthogonal(model, edge, s, BpmnDiagramLayoutOptimizer.Side.RIGHT, t, BpmnDiagramLayoutOptimizer.Side.TOP);

        List<Waypoint> waypoints = new ArrayList<>(edge.getWaypoints());
        assertEquals(3, waypoints.size(), "an L-shape has exactly one corner between the two endpoints");
        assertOrthogonal(waypoints);
        assertPoint(s, waypoints.get(0));
        assertPoint(t, waypoints.get(waypoints.size() - 1));
    }

    @Test
    void renderConnectorWithObstacle_ZShape() {
        BpmnModelInstance model = modelWithTaskInsideSubProcess();
        BpmnEdge edge = model.getModelElementsByType(BpmnEdge.class).iterator().next();

        double[] s = {0, 0};
        double[] t = {200, 100};
        optimizer.routeOrthogonal(model, edge, s, BpmnDiagramLayoutOptimizer.Side.RIGHT, t, BpmnDiagramLayoutOptimizer.Side.LEFT);

        List<Waypoint> waypoints = new ArrayList<>(edge.getWaypoints());
        assertEquals(4, waypoints.size(), "a Z-shape between two horizontal exits has exactly two corners");
        assertOrthogonal(waypoints);
        assertPoint(s, waypoints.get(0));
        assertPoint(t, waypoints.get(waypoints.size() - 1));
    }

    @Test
    void renderConnectorWithObstacle_straightLine() {
        BpmnModelInstance model = modelWithTaskInsideSubProcess();
        BpmnEdge edge = model.getModelElementsByType(BpmnEdge.class).iterator().next();

        double[] s = {0, 50};
        double[] t = {200, 50};
        optimizer.routeOrthogonal(model, edge, s, BpmnDiagramLayoutOptimizer.Side.RIGHT, t, BpmnDiagramLayoutOptimizer.Side.LEFT);

        List<Waypoint> waypoints = new ArrayList<>(edge.getWaypoints());
        assertEquals(2, waypoints.size(), "no corners are needed when source and target already share a line");
    }

    private static void assertPoint(double[] expected, Waypoint actual) {
        assertEquals(expected[0], actual.getX(), 0.01);
        assertEquals(expected[1], actual.getY(), 0.01);
    }

    private static void assertOrthogonal(List<Waypoint> waypoints) {
        for (int i = 0; i < waypoints.size() - 1; i++) {
            double dx = Math.abs(waypoints.get(i).getX() - waypoints.get(i + 1).getX());
            double dy = Math.abs(waypoints.get(i).getY() - waypoints.get(i + 1).getY());
            assertTrue(dx < 0.5 || dy < 0.5, "segment " + i + " is diagonal: (" + waypoints.get(i).getX() + "," + waypoints.get(i).getY() + ") -> (" + waypoints.get(i + 1).getX() + "," + waypoints.get(i + 1).getY() + ")");
        }
    }

    @Test
    void handleMultipleOutgoingConnectors() {
        BpmnModelInstance model = modelWithConditionalGateway();
        Task down = model.getModelElementById("Down");
        ExclusiveGateway gateway = model.getModelElementsByType(ExclusiveGateway.class).iterator().next();

        // Three independent sources all pointing at the same node's left side.
        Bounds targetB = bounds(model, 1000, 0, 100, 300);
        Bounds src1 = bounds(model, 0, 0, 50, 50);
        Bounds src2 = bounds(model, 0, 100, 50, 50);
        Bounds src3 = bounds(model, 0, 200, 50, 50);

        List<BpmnEdge> edges = new ArrayList<>(model.getModelElementsByType(BpmnEdge.class));
        List<BpmnDiagramLayoutOptimizer.EdgeRoute> routes = List.of(
                new BpmnDiagramLayoutOptimizer.EdgeRoute(edges.get(0), gateway, down, src1, targetB, BpmnDiagramLayoutOptimizer.Side.RIGHT, BpmnDiagramLayoutOptimizer.Side.LEFT),
                new BpmnDiagramLayoutOptimizer.EdgeRoute(edges.get(1), gateway, down, src2, targetB, BpmnDiagramLayoutOptimizer.Side.RIGHT, BpmnDiagramLayoutOptimizer.Side.LEFT),
                new BpmnDiagramLayoutOptimizer.EdgeRoute(edges.get(2), gateway, down, src3, targetB, BpmnDiagramLayoutOptimizer.Side.RIGHT, BpmnDiagramLayoutOptimizer.Side.LEFT)
        );

        Map<BpmnEdge, double[]> entries = optimizer.computeEntryPoints(routes);
        List<Double> ys = entries.values().stream().map(p -> p[1]).sorted().toList();
        // Three edges fanned across a 300-tall side land at 1/4, 2/4 and 3/4 of the way down.
        assertEquals(75.0, ys.get(0), 0.01);
        assertEquals(150.0, ys.get(1), 0.01);
        assertEquals(225.0, ys.get(2), 0.01);
    }

    @Test
    void handleMultipleIncomingConnectors() {
        BpmnModelInstance model = modelWithConditionalGateway();
        ExclusiveGateway gateway = model.getModelElementsByType(ExclusiveGateway.class).iterator().next();
        Task down = model.getModelElementById("Down");

        Bounds gatewayB = bounds(model, 1000, 0, 50, 50);
        Bounds src1 = bounds(model, 0, 0, 50, 50);
        Bounds src2 = bounds(model, 0, 500, 50, 50);

        List<BpmnEdge> edges = new ArrayList<>(model.getModelElementsByType(BpmnEdge.class));
        List<BpmnDiagramLayoutOptimizer.EdgeRoute> routes = List.of(
                new BpmnDiagramLayoutOptimizer.EdgeRoute(edges.get(0), down, gateway, src1, gatewayB, BpmnDiagramLayoutOptimizer.Side.RIGHT, BpmnDiagramLayoutOptimizer.Side.LEFT),
                new BpmnDiagramLayoutOptimizer.EdgeRoute(edges.get(1), down, gateway, src2, gatewayB, BpmnDiagramLayoutOptimizer.Side.RIGHT, BpmnDiagramLayoutOptimizer.Side.LEFT)
        );

        Map<BpmnEdge, double[]> entries = optimizer.computeEntryPoints(routes);
        // A diamond only touches its bounding box at the side midpoint
        for (double[] pt : entries.values()) {
            assertEquals(25.0, pt[1], 0.01, "gateway/event targets always land on the exact side midpoint");
        }
    }

    @Test
    void handleSingleIncomingConnectorAndSingleOutgoingConnector() {
        BpmnModelInstance model = modelWithTaskInsideSubProcess();
        SubProcess subProcess = model.getModelElementById("SP");
        Task task = model.getModelElementById("T");
        UserTask other = model.getModelElementsByType(UserTask.class).iterator().next();

        Bounds spB = bounds(model, 0, 0, 500, 400);
        Bounds taskB = bounds(model, 700, 150, 100, 80);

        List<BpmnEdge> edges = new ArrayList<>(model.getModelElementsByType(BpmnEdge.class));

        List<BpmnDiagramLayoutOptimizer.EdgeRoute> routes = List.of(
                new BpmnDiagramLayoutOptimizer.EdgeRoute(edges.get(0), task, subProcess, taskB, spB, BpmnDiagramLayoutOptimizer.Side.LEFT, BpmnDiagramLayoutOptimizer.Side.RIGHT),
                new BpmnDiagramLayoutOptimizer.EdgeRoute(edges.get(1), subProcess, other, spB, taskB, BpmnDiagramLayoutOptimizer.Side.RIGHT, BpmnDiagramLayoutOptimizer.Side.LEFT)
        );

        var contested = optimizer.computeContestedExitKeys(routes);
        assertTrue(contested.contains(subProcess.getId() + "|RIGHT"));

        Map<BpmnEdge, double[]> exits = optimizer.computeExitPoints(routes, contested);
        Map<BpmnEdge, double[]> entries = optimizer.computeEntryPoints(routes);
        double exitY = exits.get(edges.get(1))[1];
        double entryY = entries.get(edges.get(0))[1];
        assertFalse(near(exitY, entryY), "a contested exit must land somewhere other than the natural entry midpoint");
    }

    @Test
    void handleSingleSubprocessOutgoingConnector() {
        BpmnModelInstance model = modelWithTaskInsideSubProcess();
        SubProcess subProcess = model.getModelElementById("SP");
        UserTask other = model.getModelElementsByType(UserTask.class).iterator().next();

        Bounds spB = bounds(model, 0, 0, 500, 400);
        Bounds tgtB = bounds(model, 700, 150, 100, 80);

        List<BpmnEdge> edges = new ArrayList<>(model.getModelElementsByType(BpmnEdge.class));
        List<BpmnDiagramLayoutOptimizer.EdgeRoute> routes = List.of(
                new BpmnDiagramLayoutOptimizer.EdgeRoute(edges.get(0), subProcess, other, spB, tgtB, BpmnDiagramLayoutOptimizer.Side.RIGHT, BpmnDiagramLayoutOptimizer.Side.LEFT)
        );

        var contested = optimizer.computeContestedExitKeys(routes);
        assertTrue(contested.isEmpty());

        Map<BpmnEdge, double[]> exits = optimizer.computeExitPoints(routes, contested);
        double exitY = exits.get(edges.get(0))[1];
        assertEquals(200.0, exitY, 0.01, "with nothing to collide with, a lone exit should sit on the true midpoint");
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0;
    }


    @Test
    void shapesOverlap_detectsOverlappingRectangles() {
        BpmnModelInstance model = Bpmn.createEmptyModel();
        Bounds a = bounds(model, 0, 0, 100, 100);
        Bounds b = bounds(model, 50, 50, 100, 100);
        assertTrue(BpmnDiagramLayoutOptimizer.shapesOverlap(a, b));
    }

    @Test
    void shapesOverlap_falseForSeparateRectangles() {
        BpmnModelInstance model = Bpmn.createEmptyModel();
        Bounds a = bounds(model, 0, 0, 100, 100);
        Bounds b = bounds(model, 200, 200, 100, 100);
        assertFalse(BpmnDiagramLayoutOptimizer.shapesOverlap(a, b));
    }
}
