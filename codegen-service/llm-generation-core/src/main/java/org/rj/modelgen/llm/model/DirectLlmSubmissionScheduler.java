package org.rj.modelgen.llm.model;

import org.rj.modelgen.llm.response.ModelResponse;
import reactor.core.publisher.Mono;

/**
 * Pass-through scheduler with no throttling.
 * Used when maxConcurrentLlmCalls is not configured (≤ 0).
 */
public class DirectLlmSubmissionScheduler implements LlmSubmissionScheduler {

    @Override
    public Mono<ModelResponse> schedule(Mono<ModelResponse> work) {
        return work;
    }

    @Override
    public void dispose() {
        // nothing to release
    }

    @Override
    public boolean isDisposed() {
        return false;
    }
}
