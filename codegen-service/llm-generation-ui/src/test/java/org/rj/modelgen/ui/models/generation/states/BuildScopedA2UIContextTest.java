package org.rj.modelgen.ui.models.generation.states;

import org.junit.jupiter.api.Test;
import org.rj.modelgen.llm.state.ModelInterfacePayload;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.rj.modelgen.ui.models.generation.data.UIGenerationModelInputPayload;
import org.rj.modelgen.ui.models.generation.data.UIImpactAnalysisResult;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildScopedA2UIContextTest {

    private static final String ORIGINAL_JSONL =
            "{\"version\":\"v0.9\",\"createSurface\":{\"surfaceId\":\"s\"}}\n"
            + "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s\",\"components\":["
            + "{\"id\":\"root\",\"component\":\"Column\",\"children\":[\"name\"]},"
            + "{\"id\":\"name\",\"component\":\"TextField\"}]}}";

    private static ModelInterfacePayload run(String originalJsonl, UIImpactAnalysisResult impact) {
        final var payload = new ModelInterfacePayload();
        if (originalJsonl != null) payload.put(UIGenerationModelInputPayload.ORIGINAL_A2UI_JSONL, originalJsonl);
        if (impact != null) payload.put(UIGenerationModelInputPayload.IMPACT_ANALYSIS, impact);

        final var signal = new ModelInterfaceSignal(StandardSignals.SUCCESS);
        signal.setPayload(payload);
        new BuildScopedA2UIContext().invoke(signal).block();
        return payload;
    }

    private static UIImpactAnalysisResult impact(List<String> affected, boolean add, List<String> remove) {
        return new UIImpactAnalysisResult(affected, add, remove, "formal analysis", "commentary");
    }

    private static boolean isScoped(ModelInterfacePayload payload) {
        return payload.getOrElse(UIGenerationModelInputPayload.SCOPED_A2UI_MASKING_INSTRUCTIONS, (String) null) != null;
    }

    @Test
    void noOps_whenOriginalA2UIMissing() {
        final var payload = run(null, impact(List.of("name"), false, List.of()));
        assertFalse(isScoped(payload), "Without a merge base there is nothing to scope against");
    }

    @Test
    void noOps_whenImpactAnalysisMissing() {
        final var payload = run(ORIGINAL_JSONL, null);
        assertFalse(isScoped(payload));
    }

    @Test
    void noOps_whenAnalysisReportsNoChanges() {
        final var payload = run(ORIGINAL_JSONL, impact(List.of(), false, List.of()));
        assertFalse(isScoped(payload));
    }

    @Test
    void publishesIdSetsAndMaskingInstructions() {
        final var payload = run(ORIGINAL_JSONL, impact(List.of("name"), true, List.of("old")));

        assertEquals(Set.of("name"), payload.get(UIGenerationModelInputPayload.SCOPED_A2UI_COMPONENT_IDS));
        assertEquals(Set.of("old"), payload.get(UIGenerationModelInputPayload.SCOPED_A2UI_REMOVE_IDS));

        final String masking = payload.get(UIGenerationModelInputPayload.SCOPED_A2UI_MASKING_INSTRUCTIONS);
        assertNotNull(masking);
        assertTrue(masking.contains("name"), "Affected ids should be listed");
        assertTrue(masking.contains("old"), "Removed ids should be listed");
        assertTrue(masking.contains("yes"), "addComponents=true should be reflected");
        assertFalse(masking.contains("{{"), "All placeholders should be substituted");
    }

    @Test
    void maskingInstructions_reflectAddComponentsFalse() {
        final var payload = run(ORIGINAL_JSONL, impact(List.of("name"), false, List.of()));

        final String masking = payload.get(UIGenerationModelInputPayload.SCOPED_A2UI_MASKING_INSTRUCTIONS);
        assertTrue(masking.contains("no - do not add any new components"));
        assertTrue(masking.contains("(none)"), "An empty remove set should render as (none)");
    }

    @Test
    void scopesPureRemoveRequest() {
        final var payload = run(ORIGINAL_JSONL, impact(List.of(), false, List.of("name")));
        assertTrue(isScoped(payload), "A removal on its own is still a change worth scoping");
    }
}
