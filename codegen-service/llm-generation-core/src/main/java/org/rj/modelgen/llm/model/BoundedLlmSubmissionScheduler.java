package org.rj.modelgen.llm.model;

import org.rj.modelgen.llm.exception.LlmGenerationModelException;
import org.rj.modelgen.llm.response.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Bounded concurrency scheduler for LLM submissions.
 *
 * Uses a unicast sink  with a flatMap concurrency cap. All calls from the model generatio process
 * (outer model, every submodel, ui models) compete for the same fixed number of slots.
 * Excess work queues in the unbounded onBackpressureBuffer until a slot frees.
 */
public class BoundedLlmSubmissionScheduler implements LlmSubmissionScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(BoundedLlmSubmissionScheduler.class);

    private final Sinks.Many<Mono<ModelResponse>> workQueue;
    private final Disposable processor;
    private volatile boolean disposed = false;

    public BoundedLlmSubmissionScheduler(int maxConcurrent) {
        LOG.info("LLM submission scheduler initialised with concurrency limit of {}", maxConcurrent);
        this.workQueue = Sinks.many().unicast().onBackpressureBuffer();
        this.processor = workQueue.asFlux()
                .flatMap(work -> work.onErrorResume(e -> {
                    LOG.debug("LLM submission slot freed after error: {}", e.getMessage());
                    return Mono.empty();
                }), maxConcurrent)
                .subscribe(
                        __ -> {},
                        err -> LOG.error("LLM submission scheduler processor terminated unexpectedly — no further submissions will be processed", err)
                );
    }

    @Override
    public Mono<ModelResponse> schedule(Mono<ModelResponse> work) {
        return Mono.create(sink -> {
            if (disposed) {
                sink.error(new LlmGenerationModelException("LLM submission scheduler is disposed"));
                return;
            }

            final Mono<ModelResponse> tracked = work
                    .doOnNext(sink::success)
                    .doOnError(sink::error)
                    .onErrorResume(__ -> Mono.empty());

            Sinks.EmitResult result;
            do {
                result = workQueue.tryEmitNext(tracked);
            } while (result == Sinks.EmitResult.FAIL_NON_SERIALIZED);

            if (result.isFailure()) {
                sink.error(new LlmGenerationModelException("Failed to enqueue LLM call: " + result));
            }
        });
    }

    @Override
    public void dispose() {
        disposed = true;
        workQueue.tryEmitComplete();
        processor.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
