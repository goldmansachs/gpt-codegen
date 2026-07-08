package org.rj.modelgen.llm.models.generation.multilevel;

import org.rj.modelgen.llm.util.StringSerializable;

public enum MultiLevelGenerationModelStates implements StringSerializable {
    StartMultiLevelGeneration,
    SanitizingPrePass,
    PreProcessing,
    GenerateSubproblems,
    ParallelSubproblemExecution,
    ExecuteHighLevel,
    ValidateHighLevel,
    ReverseRender,
    GenerateReverseRenderSubproblems,
    ParallelReverseRenderSubproblemExecution,
    ExecuteDetailLevel,
    InitialValidateDetailLevel,
    ValidateDetailLevel,
    CombineReverseRenderSubproblems,
    CombineSubproblems,
    PostProcessing,
    GenerateModel,
    SubproblemUIGenerationComplete,
    Complete;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }

    public String description()
    {
        return switch (this) {
            case StartMultiLevelGeneration -> "Starting multi-level workflow generation process";
            case SanitizingPrePass -> "Sanitizing provided input";
            case PreProcessing -> "Pre-processing provided input";
            case GenerateSubproblems -> "Decomposing the input into sub-problems";
            case ParallelSubproblemExecution -> "Executing sub-problems in parallel";
            case ExecuteHighLevel -> "Generating high-level intermediate model";
            case ValidateHighLevel -> "Validating high-level intermediate model";
            case ReverseRender -> "Translating the automation model to intermediate model";
            case GenerateReverseRenderSubproblems -> "Decomposing the intermediate model into sub-problems";
            case ParallelReverseRenderSubproblemExecution -> "Executing reverse-render sub-problems in parallel";
            case ExecuteDetailLevel -> "Executing detail-level intermediate model";
            case InitialValidateDetailLevel -> "Performing initial validation of reverse-rendered detail-level intermediate model";
            case ValidateDetailLevel -> "Validating detail-level intermediate model schema";
            case CombineSubproblems -> "Combining results from sub-problems";
            case CombineReverseRenderSubproblems -> "Combining results from sub-problems";
            case PostProcessing -> "Processing final intermediate model";
            case GenerateModel -> "Generating final workflow";
            case SubproblemUIGenerationComplete -> "Completed all UI model generation";
            case Complete -> "Multi-level generation process complete";
        };
    }
}
