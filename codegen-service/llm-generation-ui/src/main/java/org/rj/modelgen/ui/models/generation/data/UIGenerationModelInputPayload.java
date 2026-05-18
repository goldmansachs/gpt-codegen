package org.rj.modelgen.ui.models.generation.data;

import org.rj.modelgen.llm.state.ModelInterfaceInputPayload;

/**
 * Input payload for the UI generation pipeline.
 * Carries the session ID and user request through the pipeline stages.
 */
public class UIGenerationModelInputPayload extends ModelInterfaceInputPayload {

    public static final String SANITIZED_REQUEST = "sanitizedRequest";
    public static final String FORMALISED_INTENT = "formalisedIntent";
    public static final String UI_OUTPUT = "uiOutput";

    public UIGenerationModelInputPayload(String sessionId, String request) {
        super(sessionId, request, null);
    }

    public String getSanitizedRequest() {
        return get(SANITIZED_REQUEST);
    }

    public void setSanitizedRequest(String sanitizedRequest) {
        put(SANITIZED_REQUEST, sanitizedRequest);
    }

    public String getFormalisedIntent() {
        return get(FORMALISED_INTENT);
    }

    public void setFormalisedIntent(String formalisedIntent) {
        put(FORMALISED_INTENT, formalisedIntent);
    }

    public String getUIOutput() {
        return get(UI_OUTPUT);
    }

    public void setUIOutput(String uiOutput) {
        put(UI_OUTPUT, uiOutput);
    }
}
