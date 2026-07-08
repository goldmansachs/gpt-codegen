package org.rj.modelgen.llm.subproblem.config;

import org.rj.modelgen.llm.state.ModelInterfacePayload;
import org.rj.modelgen.llm.subproblem.data.SubproblemExecutionResult;
import reactor.core.publisher.Mono;

public interface SubproblemParallelExecutor {
    Mono<SubproblemExecutionResult> execute(int subproblemId, ModelInterfacePayload sharedPayload);
}
