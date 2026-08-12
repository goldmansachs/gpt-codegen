package org.rj.modelgen.ui.models.generation.data;

import org.rj.modelgen.llm.state.ModelInterfaceInputPayload;

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
}
