package org.rj.modelgen.llm.model;

import org.rj.modelgen.llm.response.ModelResponse;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

/**
 * Governs how LLM submission requests are scheduled and throttled.
 */
public interface LlmSubmissionScheduler extends Disposable {
    Mono<ModelResponse> schedule(Mono<ModelResponse> work);
}
