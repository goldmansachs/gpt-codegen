package org.rj.modelgen.forms.models.generation;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Interface for form generation execution models.
 * Implementations execute the form generation pipeline which sanitizes input,
 * formalises intent, and converts to A2UI format.
 */
public interface FormGenerationExecutionModel {
    Mono<FormGenerationResult> executeModel(String sessionId, String request, Map<String, Object> data);
}
