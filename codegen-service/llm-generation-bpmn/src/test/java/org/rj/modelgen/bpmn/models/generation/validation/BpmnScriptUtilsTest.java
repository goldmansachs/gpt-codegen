package org.rj.modelgen.bpmn.models.generation.validation;

import org.junit.jupiter.api.Test;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariable;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BpmnScriptUtilsTest {

    private static BpmnGlobalVariableLibrary currentUserIdLibrary() {
        BpmnGlobalVariable currentUserId = new BpmnGlobalVariable();
        currentUserId.setName("currentUserId");
        currentUserId.setResolveValue("engineContext.currentUserId");
        return new BpmnGlobalVariableLibrary(List.of(currentUserId));
    }

    @Test
    public void isExpression_recognizesGetVariableCall() {
        assertTrue(BpmnScriptUtils.isExpression("getVariable('x')"));
    }

    @Test
    public void isExpression_recognizesGetGlobalVariableCall() {
        assertTrue(BpmnScriptUtils.isExpression("getGlobalVariable('currentUserId', [])"));
    }

    @Test
    public void isExpression_rejectsSetVariableCall() {
        assertFalse(BpmnScriptUtils.isExpression("setVariable('x', 1, 'Number')"));
    }

    @Test
    public void isExpression_rejectsMultilineValue() {
        assertFalse(BpmnScriptUtils.isExpression("getGlobalVariable('currentUserId', [])\nreturn true"));
    }

    @Test
    public void isExpression_rejectsPlainConstant() {
        assertFalse(BpmnScriptUtils.isExpression("some plain value"));
    }

    @Test
    public void reverseThenForwardResolution_roundTripsGlobalVariableExpression() {
        BpmnGlobalVariableLibrary globalVariableLibrary = currentUserIdLibrary();
        BpmnComponentLibrary componentLibrary = BpmnComponentLibrary.fullLibrary();

        String original = "${engineContext.currentUserId}";

        String reversed = BpmnScriptUtils.formatValue(original, componentLibrary, globalVariableLibrary);
        assertEquals("getGlobalVariable('currentUserId', [])", reversed);
        assertTrue(BpmnScriptUtils.isExpression(reversed));

        String forward = BpmnScriptUtils.resolveVariableReads(reversed, componentLibrary, true);
        forward = BpmnScriptUtils.resolveGlobalVariableReads(forward, globalVariableLibrary, true);
        assertEquals(original, forward);
    }

    // --- escapeGroovy ---

    @Test
    public void escapeGroovy_escapesBackslashQuoteAndDollarInThatOrder() {
        String input = "say \"hi\" then $x \\ done";
        String expected = "say \\\"hi\\\" then \\$x \\\\ done";
        assertEquals(expected, BpmnScriptUtils.escapeGroovy(input));
    }

    @Test
    public void escapeGroovy_nullBecomesEmptyString() {
        assertEquals("", BpmnScriptUtils.escapeGroovy(null));
    }

    // --- escapeGroovyWithSafeInterpolation ---

    @Test
    public void safeInterpolation_leavesDottedPathInterpolationLive() {
        assertEquals("Hi ${payload.name}!",
                BpmnScriptUtils.escapeGroovyWithSafeInterpolation("Hi ${payload.name}!"));
    }

    @Test
    public void safeInterpolation_leavesNonDottedPathExpressionInert() {
        // "1+1" doesn't match the safe dotted-identifier pattern, so it must stay escaped (inert).
        assertEquals("Result: \\${1+1}",
                BpmnScriptUtils.escapeGroovyWithSafeInterpolation("Result: ${1+1}"));
    }

    @Test
    public void safeInterpolation_stillEscapesQuotesOutsideInterpolation() {
        assertEquals("say \\\"hi\\\" ${payload.x}",
                BpmnScriptUtils.escapeGroovyWithSafeInterpolation("say \"hi\" ${payload.x}"));
    }

    // --- escapeGroovyAsExpression ---

    @Test
    public void asExpression_leavesAnyInterpolationLiveRegardlessOfContent() {
        assertEquals("App-${env}", BpmnScriptUtils.escapeGroovyAsExpression("App-${env}"));
        assertEquals("App-${1+1}", BpmnScriptUtils.escapeGroovyAsExpression("App-${1+1}"));
    }

    @Test
    public void asExpression_stillEscapesQuotesOutsideInterpolation() {
        assertEquals("${x}\\\"y", BpmnScriptUtils.escapeGroovyAsExpression("${x}\"y"));
    }
}
