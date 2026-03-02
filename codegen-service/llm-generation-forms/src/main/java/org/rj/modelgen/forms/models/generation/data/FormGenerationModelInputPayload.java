package org.rj.modelgen.forms.models.generation.data;

import org.rj.modelgen.llm.state.ModelInterfaceInputPayload;

/**
 * Input payload for the form generation pipeline.
 * Carries the session ID and user request through the pipeline stages.
 */
public class FormGenerationModelInputPayload extends ModelInterfaceInputPayload {

    public static final String SANITIZED_REQUEST = "sanitizedRequest";
    public static final String FORMALISED_INTENT = "formalisedIntent";
    public static final String A2UI_OUTPUT = "a2uiOutput";

    public FormGenerationModelInputPayload(String sessionId, String request) {
        super(sessionId, request);
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

    public String getA2UIOutput() {
        return get(A2UI_OUTPUT);
    }

    public void setA2UIOutput(String a2uiOutput) {
        put(A2UI_OUTPUT, a2uiOutput);
    }
}

