package org.rj.modelgen.llm.models.generation.multilevel.states;

import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceStandardSignals;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.exception.LlmRateLimitException;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.subproblem.config.SubproblemParallelExecutor;
import org.rj.modelgen.llm.subproblem.data.SubproblemDecompositionPayloadData;
import org.rj.modelgen.llm.subproblem.data.SubproblemDecompositionSignals;
import org.rj.modelgen.llm.subproblem.data.SubproblemExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.stream.IntStream;

public class ParallelSubproblemExecution extends ModelInterfaceState {
    private static final Logger LOG = LoggerFactory.getLogger(ParallelSubproblemExecution.class);

    private final SubproblemParallelExecutor executor;

    public ParallelSubproblemExecution(SubproblemParallelExecutor executor) {
        super(ParallelSubproblemExecution.class);
        this.executor = executor;
    }

    @Override
    public String getDescription() {
        return "Executing sub-problems in parallel";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal inputSignal) {
        final Integer subproblemCount = getPayload().getOrElse(
                SubproblemDecompositionPayloadData.SubproblemCount, (Integer) null);

        if (executor == null) {
            LOG.info("No parallel executor configured; using sequential subproblem execution path");
            return outboundSignal(SubproblemDecompositionSignals.BypassParallelExecution, "No parallel executor configured").mono();
        }

        if (subproblemCount == null || subproblemCount <= 1) {
            LOG.info("Subproblem count is {}; bypassing parallel execution and using sequential path", subproblemCount);
            return outboundSignal(SubproblemDecompositionSignals.BypassParallelExecution, "Single subproblem or no decomposition so using sequential flow").mono();
        }

        LOG.info("Launching {} subproblems in parallel", subproblemCount);

        final var payload = getPayload();
        return Flux.fromStream(IntStream.range(0, subproblemCount).boxed())
                .flatMap(id -> executor.execute(id, payload)
                        .doOnNext(result -> LOG.info("Subproblem {} completed successfully", id))
                        .onErrorResume(e -> {
                            if (isRateLimitError(e)) {
                                LOG.warn("Rate limitting (429) detected for subproblem {} - falling back to sequential execution", id);
                                return Mono.just(SubproblemExecutionResult.rateLimited(id));
                            }
                            LOG.error("Subproblem {} failed: {}", id, e.getMessage(), e);
                            return Mono.just(SubproblemExecutionResult.failed(id, e.getMessage()));
                        }))
                .collectList()
                .flatMap(unsorted -> {
                    // Sort by subproblemId to guarantee deterministic merge order (starting payload from SP0, etc.)
                    final var results = unsorted.stream()
                            .sorted(java.util.Comparator.comparingInt(SubproblemExecutionResult::subproblemId))
                            .toList();
                    final boolean anyRateLimited = results.stream().anyMatch(SubproblemExecutionResult::rateLimited);
                    if (anyRateLimited) {
                        return outboundSignal(SubproblemDecompositionSignals.BypassParallelExecution,  "Rate limit (429) hit. Falling back to sequential subproblem execution").mono();
                    }

                    // Store per-subproblem results and assets under indexed keys only.
                    int succeeded = 0;
                    SubproblemExecutionResult mainProcessResult = null;
                    for (SubproblemExecutionResult result : results) {
                        if (result.dlModelContent() != null) {
                            getPayload().put(subproblemResultContentKey(result.subproblemId()), result.dlModelContent());
                            succeeded++;
                        } else if (result.subproblemId() == 0) {
                            mainProcessResult = result;
                        }
                        if (result.assets() != null) {
                            getPayload().put(subproblemAssetsKey(result.subproblemId()), result.assets());
                        }
                    }

                    if (succeeded == 0) {
                        return error("All " + subproblemCount + " subproblems failed during parallel execution");
                    }

                    if (mainProcessResult != null) {
                        final String sessionId = getPayload().getOrElse(StandardModelData.SessionId, null);
                        final String detail = mainProcessResult.errorMessage() != null ? mainProcessResult.errorMessage() : "Main process subproblem failed during parallel execution";
                        return outboundSignal(new ModelInterfaceStandardSignals.FAIL_LLM_PROVIDER_ERROR(getId(), detail, sessionId)).mono();
                    }

                    LOG.info("Parallel subproblem execution complete: {}/{} succeeded", succeeded, subproblemCount);
                    return outboundSignal(SubproblemDecompositionSignals.SubproblemDecompositionCompleted, "All subproblems executed in parallel").mono();
                });
    }

    private static boolean isRateLimitError(Throwable e) {
        return e instanceof LlmRateLimitException;
    }

    private String subproblemResultContentKey(int id) {
        return String.format("%s-%d", SubproblemDecompositionPayloadData.SubproblemResultContent, id);
    }

    private String subproblemAssetsKey(int id) {
        return String.format("%s-%d", SubproblemDecompositionPayloadData.SubproblemAssetsContent, id);
    }

}
