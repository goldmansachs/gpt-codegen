package org.rj.modelgen.ui.models.generation.states;

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

        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_COMPONENT_IDS, affectedIds);
        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_REMOVE_IDS, removeIds);
        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_MASKING_INSTRUCTIONS,
                buildMaskingInstructions(affectedIds, removeIds, impact.isAddComponents()));

        LOG.info("Scoped generation enabled: {} affected, {} to remove, addComponents={}",
                affectedIds.size(), removeIds.size(), impact.isAddComponents());

        return Mono.just(Result.Ok());
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
