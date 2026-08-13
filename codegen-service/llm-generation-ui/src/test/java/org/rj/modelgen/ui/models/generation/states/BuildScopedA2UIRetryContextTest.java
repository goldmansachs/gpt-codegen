package org.rj.modelgen.ui.models.generation.states;

import org.junit.jupiter.api.Test;
import org.rj.modelgen.llm.state.ModelInterfacePayload;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.rj.modelgen.ui.models.generation.data.UIGenerationModelInputPayload;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildScopedA2UIRetryContextTest {

    private static ModelInterfacePayload run(Set<String> previousScope, Set<String> failed) {
        final var payload = new ModelInterfacePayload();
        if (previousScope != null) {
            payload.put(UIGenerationModelInputPayload.PREVIOUS_SCOPED_A2UI_COMPONENT_IDS, previousScope);
        }
        if (failed != null) {
            payload.put(UIGenerationModelInputPayload.FAILED_COMPONENT_IDS, failed);
        }

        final var signal = new ModelInterfaceSignal(StandardSignals.SUCCESS);
        signal.setPayload(payload);
        new BuildScopedA2UIRetryContext().invoke(signal).block();
        return payload;
    }

    private static String masking(ModelInterfacePayload payload) {
        return payload.getOrElse(UIGenerationModelInputPayload.SCOPED_A2UI_MASKING_INSTRUCTIONS, (String) null);
    }

    @Test
    void noOps_whenPreviousPassWasNotScoped() {
        final var payload = run(null, Set.of("broken"));

        assertNull(masking(payload), "An unscoped pass must keep retrying unscoped");
        assertNull(payload.getOrElse(UIGenerationModelInputPayload.SCOPED_A2UI_RETRY, (Boolean) null));
    }

    @Test
    void widensScopeToIncludeFailingComponents() {
        // The real case: the failures were on components the model never touched
        final var payload = run(Set.of("name"), Set.of("same_as_registered", "confirm_terms"));

        final Set<String> scope = payload.get(UIGenerationModelInputPayload.SCOPED_A2UI_COMPONENT_IDS);
        assertEquals(Set.of("name", "same_as_registered", "confirm_terms"), scope,
                "The retry must be able to reach components the previous scope excluded");
    }

    @Test
    void marksThePassAsARetryAndClearsRemovals() {
        final var payload = run(Set.of("name"), Set.of("name"));

        assertEquals(Boolean.TRUE, payload.get(UIGenerationModelInputPayload.SCOPED_A2UI_RETRY));
        assertEquals(Set.of(), payload.get(UIGenerationModelInputPayload.SCOPED_A2UI_REMOVE_IDS),
                "Removals were applied to the merge base already and must not be repeated");
    }

    @Test
    void maskingInstructionsListTheRetryScope() {
        final var payload = run(Set.of("name"), Set.of("email"));

        final String masking = masking(payload);
        assertNotNull(masking);
        assertTrue(masking.contains("SCOPED RETRY"));
        assertTrue(masking.contains("name") && masking.contains("email"));
        assertFalse(masking.contains("{{"), "All placeholders should be substituted");
    }

    @Test
    void retriesWithTheOriginalScope_whenNoComponentWasAttributed() {
        // Validation can fail on envelope/tree issues that carry no componentId
        final var payload = run(Set.of("name"), Set.of());

        assertEquals(Set.of("name"), payload.get(UIGenerationModelInputPayload.SCOPED_A2UI_COMPONENT_IDS));
        assertNotNull(masking(payload), "Still a scoped retry, just with the original scope");
    }
}
