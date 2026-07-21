package org.rj.modelgen.llm.models.generation.multilevel.data;

public enum MultiLevelModelStandardPayloadData {
    Model,
    Request,
    ProcessVariables,
    HighLevelModel,
    DetailLevelModel,
    LlmDirectedRetryReason,
    LlmDirectedRetryCount,
    AddPlaceholdersForUnknownActions,
    SerializedReverseRender,
    ReverseRenderedIntermediateModel,
    ScopedDetailLevelModel,
    ImpactAnalysis,
    ImpactAnalysisMaskingInstructions,
    OriginalDetailLevelModel,
    InitialValidations,
    ModelFactory,
    UIComponents,
    OriginalCanvasLayout;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }
}
