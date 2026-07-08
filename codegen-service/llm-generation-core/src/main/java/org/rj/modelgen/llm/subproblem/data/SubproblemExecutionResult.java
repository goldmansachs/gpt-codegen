package org.rj.modelgen.llm.subproblem.data;

import org.rj.modelgen.llm.intrep.assets.ModelAssets;

public record SubproblemExecutionResult(
    int subproblemId,
    String dlModelContent,
    ModelAssets<?> assets,
    boolean rateLimited,
    String errorMessage
) {
    public static SubproblemExecutionResult success(int id, String dlModelContent, ModelAssets<?> assets) {
        return new SubproblemExecutionResult(id, dlModelContent, assets, false, null);
    }

    public static SubproblemExecutionResult rateLimited(int id) {
        return new SubproblemExecutionResult(id, null, null, true, null);
    }

    public static SubproblemExecutionResult failed(int id, String errorMessage) {
        return new SubproblemExecutionResult(id, null, null, false, errorMessage);
    }
}
