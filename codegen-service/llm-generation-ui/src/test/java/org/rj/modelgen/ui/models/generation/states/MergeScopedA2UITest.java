package org.rj.modelgen.ui.models.generation.states;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.rj.modelgen.llm.state.ModelInterfacePayload;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.rj.modelgen.llm.util.Util;
import org.rj.modelgen.ui.models.generation.data.UIGenerationModelInputPayload;
import org.rj.modelgen.ui.models.generation.data.UIImpactAnalysisResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class MergeScopedA2UITest {

    private static final ObjectMapper MAPPER = Util.getObjectMapper();

    private static final String CREATE_SURFACE = "{\"version\":\"v0.9\",\"createSurface\":{\"surfaceId\":\"s\"}}";

    /** root -> [name, email]; email carries a visibility rule keyed off name. */
    private static final String ORIGINAL = CREATE_SURFACE + "\n"
            + "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s\",\"components\":["
            + "{\"id\":\"formConfig\",\"component\":\"FormConfig\",\"formId\":\"f1\"},"
            + "{\"id\":\"root\",\"component\":\"Column\",\"children\":[\"name\",\"email\"]},"
            + "{\"id\":\"name\",\"component\":\"TextField\",\"label\":\"Name\"},"
            + "{\"id\":\"email\",\"component\":\"TextField\",\"label\":\"Email\","
            + "\"visibility\":{\"args\":{\"rules\":[{\"checks\":[{\"input\":\"name\",\"op\":\"eq\"}]}]}}}"
            + "]}}";

    private static String generated(String componentsJson) {
        return "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s\",\"components\":[" + componentsJson + "]}}";
    }

    private record MergeResult(String jsonl, ModelInterfacePayload payload) {
        List<JsonNode> components() {
            try {
                for (String line : jsonl.strip().lines().toList()) {
                    final JsonNode node = MAPPER.readTree(line);
                    final JsonNode components = node.path("updateComponents").path("components");
                    if (components.isArray()) {
                        return StreamSupport.stream(components.spliterator(), false).toList();
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return List.of();
        }

        List<String> componentIds() {
            return components().stream().map(c -> c.path("id").asText()).toList();
        }

        JsonNode component(String id) {
            return components().stream().filter(c -> id.equals(c.path("id").asText())).findFirst().orElse(null);
        }
    }

    private static MergeResult merge(String original, String generated, UIImpactAnalysisResult impact,
                                     Set<String> allowed, Set<String> remove) {
        return merge(new MergeScopedA2UI(), original, generated, impact, allowed, remove);
    }

    private static MergeResult merge(MergeScopedA2UI state, String original, String generated,
                                     UIImpactAnalysisResult impact, Set<String> allowed, Set<String> remove) {
        final var payload = new ModelInterfacePayload();
        payload.put(UIGenerationModelInputPayload.ORIGINAL_A2UI_JSONL, original);
        payload.put(UIGenerationModelInputPayload.UI_OUTPUT, generated);
        payload.put(UIGenerationModelInputPayload.IMPACT_ANALYSIS, impact);
        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_COMPONENT_IDS, allowed);
        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_REMOVE_IDS, remove);
        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_MASKING_INSTRUCTIONS, "MASK");

        final var signal = new ModelInterfaceSignal(StandardSignals.SUCCESS);
        signal.setPayload(payload);
        state.invoke(signal).block();

        return new MergeResult(payload.get(UIGenerationModelInputPayload.UI_OUTPUT), payload);
    }

    private static UIImpactAnalysisResult impact(List<String> affected, boolean add, List<String> remove) {
        return new UIImpactAnalysisResult(affected, add, remove, "formal", "commentary");
    }

    @Test
    void modifiesOneComponent_preservingTheRest() {
        final var result = merge(ORIGINAL,
                generated("{\"id\":\"name\",\"component\":\"TextField\",\"label\":\"Full name\"}"),
                impact(List.of("name"), false, List.of()), Set.of("name"), Set.of());

        assertEquals(List.of("formConfig", "root", "name", "email"), result.componentIds(),
                "Original order must be preserved");
        assertEquals("Full name", result.component("name").path("label").asText());
        assertEquals("Email", result.component("email").path("label").asText(), "Untouched component must survive verbatim");
        assertEquals("f1", result.component("formConfig").path("formId").asText());
    }

    @Test
    void discardsOutOfScopeEdits() {
        // The LLM returns 'email' too, but the analysis only sanctioned 'name'
        final var result = merge(ORIGINAL,
                generated("{\"id\":\"name\",\"component\":\"TextField\",\"label\":\"Full name\"},"
                        + "{\"id\":\"email\",\"component\":\"TextField\",\"label\":\"HIJACKED\"}"),
                impact(List.of("name"), false, List.of()), Set.of("name"), Set.of());

        assertEquals("Full name", result.component("name").path("label").asText());
        assertEquals("Email", result.component("email").path("label").asText(),
                "Out-of-scope edit must be discarded in favour of the original");
    }

    @Test
    void addsNewComponentReferencedByUpdatedParent() {
        final var result = merge(ORIGINAL,
                generated("{\"id\":\"root\",\"component\":\"Column\",\"children\":[\"name\",\"email\",\"phone\"]},"
                        + "{\"id\":\"phone\",\"component\":\"TextField\",\"label\":\"Phone\"}"),
                impact(List.of("root"), true, List.of()), Set.of("root"), Set.of());

        assertTrue(result.componentIds().contains("phone"), "New component should be appended");
        assertEquals("Phone", result.component("phone").path("label").asText());
        assertEquals(3, result.component("root").path("children").size());
    }

    @Test
    void removesLeafAndCleansReferences() {
        final var result = merge(ORIGINAL,
                generated("{\"id\":\"root\",\"component\":\"Column\",\"children\":[\"email\"]}"),
                impact(List.of("root"), false, List.of("name")), Set.of("root"), Set.of("name"));

        assertFalse(result.componentIds().contains("name"), "Removed component must be gone");

        final JsonNode email = result.component("email");
        assertTrue(email.path("visibility").isMissingNode(),
                "Behaviour modifier referencing the removed component should be pruned entirely");
    }

    @Test
    void treatsUnreturnedAffectedComponentAsImplicitRemoval() {
        // 'name' is flagged as affected but the LLM does not return it
        final var result = merge(ORIGINAL,
                generated("{\"id\":\"root\",\"component\":\"Column\",\"children\":[\"email\"]}"),
                impact(List.of("root", "name"), false, List.of()), Set.of("root", "name"), Set.of());

        assertFalse(result.componentIds().contains("name"), "Unreturned affected component is implicitly removed");
    }

    @Test
    void reparentsNewlyOrphanedComponentButLeavesPreExistingOrphans() {
        // root drops 'email' from children without removing it -> newly orphaned
        final var result = merge(ORIGINAL,
                generated("{\"id\":\"root\",\"component\":\"Column\",\"children\":[\"name\"]}"),
                impact(List.of("root"), false, List.of()), Set.of("root"), Set.of());

        final List<String> rootChildren = new ArrayList<>();
        result.component("root").path("children").forEach(c -> rootChildren.add(c.asText()));

        assertTrue(rootChildren.contains("email"), "Newly orphaned component should be re-parented to root");
        assertFalse(rootChildren.contains("formConfig"),
                "formConfig was already outside the tree and must not be dragged into it");
    }

    @Test
    void neverRemovesRoot() {
        final var result = merge(ORIGINAL,
                generated("{\"id\":\"name\",\"component\":\"TextField\",\"label\":\"X\"}"),
                impact(List.of("name"), false, List.of("root")), Set.of("name"), Set.of("root"));

        assertTrue(result.componentIds().contains("root"), "root must never be removed");
    }

    @Test
    void passesThroughUnchanged_whenNotScoped() {
        final var payload = new ModelInterfacePayload();
        final String generated = generated("{\"id\":\"root\",\"component\":\"Column\",\"children\":[]}");
        payload.put(UIGenerationModelInputPayload.ORIGINAL_A2UI_JSONL, ORIGINAL);
        payload.put(UIGenerationModelInputPayload.UI_OUTPUT, generated);

        final var signal = new ModelInterfaceSignal(StandardSignals.SUCCESS);
        signal.setPayload(payload);
        new MergeScopedA2UI().invoke(signal).block();

        assertEquals(generated, payload.get(UIGenerationModelInputPayload.UI_OUTPUT),
                "Without scoping data the generated output must pass through untouched");
    }

    @Test
    void keepsOriginal_whenLlmReturnsNoComponents() {
        final var result = merge(ORIGINAL, generated(""),
                impact(List.of("name"), false, List.of()), Set.of("name"), Set.of());

        assertEquals(List.of("formConfig", "root", "name", "email"), result.componentIds(),
                "An empty response must not wipe the form");
    }

    @Test
    void refreshesMergeBaseAndClearsScopingKeys() {
        final var result = merge(ORIGINAL,
                generated("{\"id\":\"name\",\"component\":\"TextField\",\"label\":\"Full name\"}"),
                impact(List.of("name"), false, List.of()), Set.of("name"), Set.of());

        final var payload = result.payload();
        assertEquals(result.jsonl(), payload.get(UIGenerationModelInputPayload.ORIGINAL_A2UI_JSONL),
                "Merge base should be refreshed so a retry sees the merged form");

        assertNull(payload.getOrElse(UIGenerationModelInputPayload.SCOPED_A2UI_MASKING_INSTRUCTIONS, (String) null),
                "Clearing the masking instructions is what makes the next retry unscoped");
        assertNull(payload.getOrElse(UIGenerationModelInputPayload.SCOPED_A2UI_COMPONENT_IDS, (Object) null));
        assertNull(payload.getOrElse(UIGenerationModelInputPayload.SCOPED_A2UI_REMOVE_IDS, (Object) null));

        assertNotNull(payload.getOrElse(UIGenerationModelInputPayload.IMPACT_ANALYSIS, (Object) null),
                "Impact analysis stays: downstream stages still read the formal analysis");
    }

    @Test
    void preservesNonComponentMessagesFromTheOriginal() {
        final var result = merge(ORIGINAL,
                generated("{\"id\":\"name\",\"component\":\"TextField\",\"label\":\"Full name\"}"),
                impact(List.of("name"), false, List.of()), Set.of("name"), Set.of());

        assertTrue(result.jsonl().contains("createSurface"), "createSurface must be carried over from the original");
    }

    @Test
    void invokesPostMergeHookWithMergedComponents() {
        final List<String> seen = new ArrayList<>();
        final var state = new MergeScopedA2UI() {
            @Override
            protected void postMergeHook(List<ObjectNode> mergedComponents, MergeContext context) {
                mergedComponents.forEach(c -> seen.add(c.path("id").asText()));
            }
        };

        merge(state, ORIGINAL, generated("{\"id\":\"name\",\"component\":\"TextField\",\"label\":\"X\"}"),
                impact(List.of("name"), false, List.of()), Set.of("name"), Set.of());

        assertEquals(List.of("formConfig", "root", "name", "email"), seen);
    }
}
