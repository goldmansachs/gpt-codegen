package org.rj.modelgen.ui.models.generation.data;

import org.rj.modelgen.llm.state.ModelInterfaceInputPayload;

import java.util.Set;

/**
 * Input payload for the UI generation pipeline.
 * Carries the session ID and user request through the pipeline stages.
 */
public class UIGenerationModelInputPayload extends ModelInterfaceInputPayload {

    public static final String SANITIZED_REQUEST = "sanitizedRequest";
    public static final String COMMENTARY = "commentary";
    public static final String UI_OUTPUT = "uiOutput";
    public static final String IMPACT_ANALYSIS = "impactAnalysis";
    public static final String FORMAL_ANALYSIS = "formalAnalysis";
    public static final String PROMPT_HISTORY = "promptHistory";

    /**
     * Snapshot of the full existing A2UI JSONL, published by the consumer before scoped
     * generation and used as the merge base. Refreshed to the merged model by
     * {@code MergeScopedA2UI} so a retry pass sees the current state.
     */
    public static final String ORIGINAL_A2UI_JSONL = "originalA2UIJsonl";

    /** Component IDs the LLM is permitted to modify during a scoped generation pass. */
    public static final String SCOPED_A2UI_COMPONENT_IDS = "scopedA2UIComponentIds";

    /** Component IDs to drop from the model during the scoped merge. */
    public static final String SCOPED_A2UI_REMOVE_IDS = "scopedA2UIRemoveIds";

    /**
     * Masking instruction block appended to the generation prompt. Presence of this key is
     * the "scoping is active" flag - every scoped stage keys off it.
     */
    public static final String SCOPED_A2UI_MASKING_INSTRUCTIONS = "scopedA2UIMaskingInstructions";

    /**
     * The scope from the previous pass, retained across the merge so a validation failure can be
     * retried scoped rather than falling back to a full regeneration.
     */
    public static final String PREVIOUS_SCOPED_A2UI_COMPONENT_IDS = "previousScopedA2UIComponentIds";

    /** Ids of components that failed the last validation pass, published by the validator. */
    public static final String FAILED_COMPONENT_IDS = "failedComponentIds";

    /**
     * Marks the current pass as a scoped retry. The merge suppresses implicit removals when set:
     * a retry returns only the components being fixed, so anything absent must be preserved rather
     * than treated as deleted.
     */
    public static final String SCOPED_A2UI_RETRY = "scopedA2UIRetry";

    public UIGenerationModelInputPayload(String sessionId, String request) {
        super(sessionId, request, null);
    }

    public String getSanitizedRequest() {
        return get(SANITIZED_REQUEST);
    }

    public void setSanitizedRequest(String sanitizedRequest) {
        put(SANITIZED_REQUEST, sanitizedRequest);
    }


    public String getCommentary() {
        return get(COMMENTARY);
    }

    public void setCommentary(String commentary) {
        put(COMMENTARY, commentary);
    }

    public String getUIOutput() {
        return get(UI_OUTPUT);
    }

    public void setUIOutput(String uiOutput) {
        put(UI_OUTPUT, uiOutput);
    }

    public String getImpactAnalysis() {
        return get(IMPACT_ANALYSIS);
    }

    public void setImpactAnalysis(String impactAnalysis) {
        put(IMPACT_ANALYSIS, impactAnalysis);
    }

    public String getFormalAnalysis() {
        return get(FORMAL_ANALYSIS);
    }

    public void setFormalAnalysis(String formalAnalysis) {
        put(FORMAL_ANALYSIS, formalAnalysis);
    }

    public String getPromptHistory() {
        return get(PROMPT_HISTORY);
    }

    public void setPromptHistory(String promptHistory) {
        put(PROMPT_HISTORY, promptHistory);
    }

    public String getOriginalA2UIJsonl() {
        return get(ORIGINAL_A2UI_JSONL);
    }

    public void setOriginalA2UIJsonl(String originalA2UIJsonl) {
        put(ORIGINAL_A2UI_JSONL, originalA2UIJsonl);
    }

    public Set<String> getScopedA2UIComponentIds() {
        return get(SCOPED_A2UI_COMPONENT_IDS);
    }

    public void setScopedA2UIComponentIds(Set<String> scopedA2UIComponentIds) {
        put(SCOPED_A2UI_COMPONENT_IDS, scopedA2UIComponentIds);
    }

    public Set<String> getScopedA2UIRemoveIds() {
        return get(SCOPED_A2UI_REMOVE_IDS);
    }

    public void setScopedA2UIRemoveIds(Set<String> scopedA2UIRemoveIds) {
        put(SCOPED_A2UI_REMOVE_IDS, scopedA2UIRemoveIds);
    }

    public String getScopedA2UIMaskingInstructions() {
        return get(SCOPED_A2UI_MASKING_INSTRUCTIONS);
    }

    public void setScopedA2UIMaskingInstructions(String scopedA2UIMaskingInstructions) {
        put(SCOPED_A2UI_MASKING_INSTRUCTIONS, scopedA2UIMaskingInstructions);
    }

    public Set<String> getPreviousScopedA2UIComponentIds() {
        return get(PREVIOUS_SCOPED_A2UI_COMPONENT_IDS);
    }

    public Set<String> getFailedComponentIds() {
        return get(FAILED_COMPONENT_IDS);
    }
}
