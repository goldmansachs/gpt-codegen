package org.rj.modelgen.ui.models.generation.states;

import org.rj.modelgen.llm.statemodel.states.common.ExecuteLogic;
import org.rj.modelgen.llm.util.Result;
import org.rj.modelgen.llm.util.Util;
import org.rj.modelgen.ui.models.generation.data.UIGenerationModelInputPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Re-establishes scoping for a validation retry, so a failed pass is corrected in place rather
 * than by regenerating the whole model.
 *
 * <p>Without this the merge clears the mask and the retry falls back to a full regeneration,
 * which throws away the accidental-edit protection precisely when the model is least reliable.
 * The retry scope is the previous scope <em>plus</em> every component the validator flagged -
 * the offending components are frequently ones the model was never permitted to touch, so a
 * retry confined to the original scope could not fix them.</p>
 *
 * <p>No-ops when the previous pass was not scoped, leaving the unscoped retry path unchanged.</p>
 */
public class BuildScopedA2UIRetryContext extends ExecuteLogic {

    private static final Logger LOG = LoggerFactory.getLogger(BuildScopedA2UIRetryContext.class);

    private static final String MASKING_TEMPLATE =
            Util.loadStringResource("content/prompts/a2ui-scoped-retry-masking-instructions");

    private static final String PLACEHOLDER_AFFECTED_IDS = "{{AFFECTED_IDS}}";

    public BuildScopedA2UIRetryContext() {
        super(BuildScopedA2UIRetryContext.class);
    }

    @Override
    public String getDescription() {
        return "Rebuild the scoped generation context for a validation retry";
    }

    @Override
    protected Mono<Result<Void, String>> executeLogic() {
        final var payload = getPayload();

        final Set<String> previousScope = idSet(UIGenerationModelInputPayload.PREVIOUS_SCOPED_A2UI_COMPONENT_IDS);
        if (previousScope.isEmpty()) {
            LOG.debug("Previous pass was not scoped - retrying unscoped");
            return Mono.just(Result.Ok());
        }

        final Set<String> failed = idSet(UIGenerationModelInputPayload.FAILED_COMPONENT_IDS);

        final Set<String> retryScope = new LinkedHashSet<>(previousScope);
        final Set<String> newlyInScope = new LinkedHashSet<>(failed);
        newlyInScope.removeAll(previousScope);
        retryScope.addAll(failed);

        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_COMPONENT_IDS, retryScope);
        // Removals were already applied to the merge base on the previous pass; re-applying them
        // would attempt to delete components that no longer exist
        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_REMOVE_IDS, Set.<String>of());
        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_RETRY, Boolean.TRUE);
        payload.put(UIGenerationModelInputPayload.SCOPED_A2UI_MASKING_INSTRUCTIONS,
                MASKING_TEMPLATE.replace(PLACEHOLDER_AFFECTED_IDS, String.join(", ", retryScope)));

        LOG.info("Scoped retry: {} component(s) in scope ({} carried over, {} added from validation errors)",
                retryScope.size(), previousScope.size(), newlyInScope.size());

        return Mono.just(Result.Ok());
    }

    @SuppressWarnings("unchecked")
    private Set<String> idSet(String key) {
        final Object raw = getPayload().getOrElse(key, (Object) null);
        return raw instanceof Set ? new LinkedHashSet<>((Set<String>) raw) : Set.of();
    }
}
