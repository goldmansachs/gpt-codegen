package org.rj.modelgen.ui.models.generation;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Interface for UI generation execution models.
 * Implementations execute the UI generation a2ui which sanitizes input,
 * formalises intent, and converts to the target UI format.
 */
public interface UIGenerationExecutionModel {
    Mono<UIGenerationResult> executeModel(String sessionId, String request, Map<String, Object> data);
}

