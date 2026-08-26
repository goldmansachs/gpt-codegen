package org.rj.modelgen.ui.models.generation.states;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rj.modelgen.llm.statemodel.states.common.ExecuteLogic;
import org.rj.modelgen.llm.util.Result;
import org.rj.modelgen.llm.util.Util;
import org.rj.modelgen.ui.models.generation.data.UIGenerationModelInputPayload;
import org.rj.modelgen.ui.models.generation.data.UIImpactAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Derives the scoped-generation context from the impact analysis, so the following
 * generation call can be constrained to the components the analysis flagged.
 *
 * <p>The full model is still sent to the LLM for context - the protection comes from
 * {@link MergeScopedA2UI}, which discards anything returned outside the allowed set.
 * This state only publishes the id sets and the masking instruction block.</p>
 *
 * <p>No-ops when there is nothing to scope, so it is safe to leave on a path that also
 * carries unscoped traffic.</p>
 */
public class BuildScopedA2UIContext extends ExecuteLogic {

    private static final Logger LOG = LoggerFactory.getLogger(BuildScopedA2UIContext.class);

    private static final ObjectMapper MAPPER = Util.getObjectMapper();

    private static final String MASKING_TEMPLATE =
            Util.loadStringResource("content/prompts/a2ui-scoped-masking-instructions");

    private static final String PLACEHOLDER_AFFECTED_IDS = "{{AFFECTED_IDS}}";
    private static final String PLACEHOLDER_ADD_COMPONENTS = "{{ADD_COMPONENTS}}";
    private static final String PLACEHOLDER_REMOVE_IDS = "{{REMOVE_IDS}}";

    private static final String NONE = "(none)";

    public BuildScopedA2UIContext() {
        super(BuildScopedA2UIContext.class);
    }

    @Override
    public String getDescription() {
        return "Determine which A2UI components are in scope for this change";
    }

    @Override
    protected Mono<Result<Void, String>> executeLogic() {
        final var payload = getPayload();

        final String originalJsonl = payload.getOrElse(UIGenerationModelInputPayload.ORIGINAL_A2UI_JSONL, (String) null);
        if (originalJsonl == null || originalJsonl.isBlank()) {
            LOG.debug("No original A2UI available - skipping scoped generation");
            return Mono.just(Result.Ok());
        }

        final UIImpactAnalysisResult impact = EvaluateUIImpactAnalysis.getImpactAnalysis(payload);
        if (impact == null) {
            LOG.debug("No impact analysis available - skipping scoped generation");
            return Mono.just(Result.Ok());
        }

        if (impact.isNoChangeRequired()) {
            LOG.debug("Impact analysis reports no changes required - skipping scoped generation");
            return Mono.just(Result.Ok());
        }

        final Set<String> affectedIds = new LinkedHashSet<>(impact.getAffectedComponentIds());
        final Set<String> removeIds = new LinkedHashSet<>(impact.getRemoveComponentIds());

        adjustScope(affectedIds, removeIds);

        warnOnUnknownIds(originalJsonl, affectedIds, removeIds);

        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_COMPONENT_IDS, affectedIds);
        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_REMOVE_IDS, removeIds);
        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_MASKING_INSTRUCTIONS,
                buildMaskingInstructions(affectedIds, removeIds, impact.isAddComponents()));

        LOG.info("Scoped generation enabled: {} affected, {} to remove, addComponents={}",
                affectedIds.size(), removeIds.size(), impact.isAddComponents());

        return Mono.just(Result.Ok());
    }

    /**
     * Extension point for target-specific scope adjustments, invoked before the id sets are
     * published and the masking instructions rendered. Mirrors
     * {@link MergeScopedA2UI#postMergeHook}: the analysis reasons about the visible component
     * tree, so a target with components outside it can widen the scope here. The default
     * implementation does nothing.
     *
     * @param affectedIds ids the LLM may modify, mutable
     * @param removeIds   ids to be removed, mutable
     */
    protected void adjustScope(Set<String> affectedIds, Set<String> removeIds) {
        // No-op by default
    }

    /**
     * Flags impact-analysis ids that do not exist in the model the merge will run against.
     *
     * <p>The merge matches purely by id, so an unknown id fails silently: nothing is replaced or
     * removed, whatever the model returns under that id looks brand new, and it gets appended and
     * then re-parented to root. The result is a plausible-looking model with duplicates bolted on
     * the end. This is the symptom of the analysis and the merge disagreeing about id space, which
     * is worth saying out loud rather than leaving to be spotted in the output.</p>
     */
    private void warnOnUnknownIds(String originalJsonl, Set<String> affectedIds, Set<String> removeIds) {
        final Set<String> known = componentIdsIn(originalJsonl);
        if (known.isEmpty()) return;

        final Set<String> unknown = new LinkedHashSet<>();
        affectedIds.stream().filter(id -> !known.contains(id)).forEach(unknown::add);
        removeIds.stream().filter(id -> !known.contains(id)).forEach(unknown::add);

        if (!unknown.isEmpty()) {
            LOG.warn("Impact analysis reported {} component id(s) that do not exist in the model being edited: {}. "
                    + "These cannot be matched during the merge and will have no effect. This usually means the "
                    + "analysis ran against a different representation of the UI than the one being merged into.",
                    unknown.size(), unknown);
        }
    }

    private Set<String> componentIdsIn(String jsonl) {
        final Set<String> ids = new LinkedHashSet<>();
        try {
            for (String line : jsonl.strip().lines().toList()) {
                if (line.isBlank()) continue;
                final JsonNode components = MAPPER.readTree(line).path("updateComponents").path("components");
                if (!components.isArray()) continue;
                for (JsonNode component : components) {
                    if (component.path("id").isTextual()) ids.add(component.get("id").asText());
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not index component ids for the scope check: {}", e.getMessage());
        }
        return ids;
    }

    private static String buildMaskingInstructions(Set<String> affectedIds, Set<String> removeIds, boolean addComponents) {
        return MASKING_TEMPLATE
                .replace(PLACEHOLDER_AFFECTED_IDS, formatIds(affectedIds))
                .replace(PLACEHOLDER_ADD_COMPONENTS, addComponents
                        ? "yes - add the new components the request requires"
                        : "no - do not add any new components")
                .replace(PLACEHOLDER_REMOVE_IDS, formatIds(removeIds));
    }

    private static String formatIds(Set<String> ids) {
        return ids.isEmpty() ? NONE : String.join(", ", ids);
    }
}
