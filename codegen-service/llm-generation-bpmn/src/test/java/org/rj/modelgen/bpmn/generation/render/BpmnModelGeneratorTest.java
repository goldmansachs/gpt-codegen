package org.rj.modelgen.bpmn.generation.render;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.llm.intrep.IntermediateModelParser;
import org.rj.modelgen.llm.util.Util;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BpmnModelGeneratorTest {

    private static final double TOLERANCE = 1.0; // to avoid floating-point rounding from causing false failures

    private static BpmnModelInstance render(String resourcePath) {
        String json = Util.loadStringResource(resourcePath);
        BpmnIntermediateModel intermediateModel = new IntermediateModelParser<>(BpmnIntermediateModel.class)
                .parse(json)
                .orElseThrow(err -> new IllegalStateException("Failed to parse " + resourcePath + ": " + err));
        final var result = new BasicBpmnModelGenerator().generateModel(intermediateModel);

        final var modelInstance = result.getValue();

        try {
            final var bpmnXml = Bpmn.convertToString(modelInstance);
            System.out.println(bpmnXml);
        } catch (org.camunda.bpm.model.xml.ModelValidationException ex) {
           System.err.println("Failed to render: " + ex.getMessage());
        }

        return new BasicBpmnModelGenerator().generateModel(intermediateModel)
                .orElseThrow(err -> new IllegalStateException("Failed to generate model for " + resourcePath + ": " + err));
    }

    @Test
    void example1_rendersBPMN() {
        validateRenderingRules(render("generation-examples/base/input/example-1-input.json"));
    }

    @Test
    void example2_rendersBPMN() {
        validateRenderingRules(render("generation-examples/base/input/example-2-input.json"));
    }

    @Test
    void example3_rendersBPMN() {
        validateRenderingRules(render("generation-examples/base/input/example-3-input.json"));
    }

    @Test
    void example4_rendersBPMN() {
        validateRenderingRules(render("generation-examples/base/input/example-4-input.json"));
    }

    @Test
    void example5_rendersBPMN() {
        validateRenderingRules(render("generation-examples/base/input/example-5-input.json"));
    }

    @Test
    void example6_rendersBPMN() {
        validateRenderingRules(render("generation-examples/base/input/example-6-input.json"));
    }

    @Test
    void example7_rendersBPMN() {
        validateRenderingRules(render("generation-examples/base/input/example-7-input.json"));
    }

    @Test
    void example8_rendersBPMN() {
        validateRenderingRules(render("generation-examples/base/input/example-8-input.json"));
    }

    private void validateRenderingRules(BpmnModelInstance model) {
        assertDoesNotThrow(() -> Bpmn.validateModel(model), "generated model must be schema-valid BPMN");
        assertAllConnectorsAreOrthogonal(model);
        assertNoUnexpectedNodeOverlaps(model);
        assertBoundaryEventsSitOnTheirHostsBoundary(model);
        assertTerminalNodesAreNotPlacedBehindTheirSource(model);
    }

    // Every connector segment must be purely horizontal or purely vertical. no diagonals
    private void assertAllConnectorsAreOrthogonal(BpmnModelInstance model) {
        for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
            List<Waypoint> waypoints = new ArrayList<>(edge.getWaypoints());
            for (int i = 0; i < waypoints.size() - 1; i++) {
                double dx = Math.abs(waypoints.get(i).getX() - waypoints.get(i + 1).getX());
                double dy = Math.abs(waypoints.get(i).getY() - waypoints.get(i + 1).getY());
                assertTrue(dx < TOLERANCE || dy < TOLERANCE, "diagonal segment on " + edgeLabel(edge) + " at index " + i);
            }
        }
    }

    private void assertNoUnexpectedNodeOverlaps(BpmnModelInstance model) {
        List<BpmnShape> shapes = new ArrayList<>();
        for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
            if (shape.getBpmnElement() != null && shape.getBounds() != null) shapes.add(shape);
        }

        for (int i = 0; i < shapes.size(); i++) {
            for (int j = i + 1; j < shapes.size(); j++) {
                var a = shapes.get(i);
                var b = shapes.get(j);
                if (!boundsOverlap(a.getBounds(), b.getBounds())) continue;
                if (isExpectedOverlap(a.getBpmnElement(), b.getBpmnElement())) continue;

                fail("unexpected overlap between " + a.getBpmnElement().getId() + " and " + b.getBpmnElement().getId());
            }
        }
    }

    private boolean isExpectedOverlap(ModelElementInstance a, ModelElementInstance b) {
        return isSubProcessAndDescendant(a, b) || isSubProcessAndDescendant(b, a)
                || isBoundaryEventAndHost(a, b) || isBoundaryEventAndHost(b, a);
    }

    private boolean isSubProcessAndDescendant(ModelElementInstance maybeAncestor, ModelElementInstance maybeDescendant) {
        if (!(maybeAncestor instanceof SubProcess sp)) return false;
        for (ModelElementInstance p = maybeDescendant.getParentElement(); p != null; p = p.getParentElement()) {
            if (p.equals(sp)) return true;
        }
        return false;
    }

    private boolean isBoundaryEventAndHost(ModelElementInstance maybeBoundaryEvent, ModelElementInstance maybeHost) {
        return maybeBoundaryEvent instanceof BoundaryEvent be && maybeHost.equals(be.getAttachedTo());
    }

    private void assertBoundaryEventsSitOnTheirHostsBoundary(BpmnModelInstance model) {
        for (BoundaryEvent be : model.getModelElementsByType(BoundaryEvent.class)) {
            Activity host = be.getAttachedTo();
            Bounds beBounds = boundsOf(model, be.getId());
            Bounds hostBounds = host != null ? boundsOf(model, host.getId()) : null;
            if (beBounds == null || hostBounds == null) continue;

            double cx = beBounds.getX() + beBounds.getWidth() / 2.0;
            double cy = beBounds.getY() + beBounds.getHeight() / 2.0;
            double left = hostBounds.getX();
            double right = hostBounds.getX() + hostBounds.getWidth();
            double top = hostBounds.getY();
            double bottom = hostBounds.getY() + hostBounds.getHeight();

            boolean onVerticalEdge = (near(cx, left) || near(cx, right)) && cy >= top - TOLERANCE && cy <= bottom + TOLERANCE;
            boolean onHorizontalEdge = (near(cy, top) || near(cy, bottom)) && cx >= left - TOLERANCE && cx <= right + TOLERANCE;

            assertTrue(onVerticalEdge || onHorizontalEdge, "boundary event " + be.getId() + " is not positioned on host " + host.getId() + "'s boundary");
        }
    }

    private void assertTerminalNodesAreNotPlacedBehindTheirSource(BpmnModelInstance model) {
        for (FlowNode node : model.getModelElementsByType(FlowNode.class)) {
            if (!node.getOutgoing().isEmpty() || node.getIncoming().size() != 1) continue;
            FlowNode source = node.getIncoming().iterator().next().getSource();
            if (source == null) continue;

            Bounds nodeBounds = boundsOf(model, node.getId());
            Bounds sourceBounds = boundsOf(model, source.getId());
            if (nodeBounds == null || sourceBounds == null) continue;

            assertTrue(nodeBounds.getX() >= sourceBounds.getX() - TOLERANCE, "terminal node " + node.getId() + " is placed behind its predecessor " + source.getId());
        }
    }

    private Bounds boundsOf(BpmnModelInstance model, String elementId) {
        for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
            if (shape.getBpmnElement() != null && elementId.equals(shape.getBpmnElement().getId())) {
                return shape.getBounds();
            }
        }
        return null;
    }

    private static boolean boundsOverlap(Bounds a, Bounds b) {
        return a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX()
                && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY();
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) <= TOLERANCE;
    }

    private static String edgeLabel(BpmnEdge edge) {
        return edge.getBpmnElement() != null ? edge.getBpmnElement().getId() : edge.getId();
    }
}
