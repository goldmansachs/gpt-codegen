package org.rj.modelgen.bpmn.models.generation.common;

import org.rj.modelgen.llm.util.StringSerializable;

public enum BpmnAdditionalModelStates implements StringSerializable {
    InitializeBpmnData,
    InitializeBpmnPayload,
    InsertSyntheticComponents,
    ProcessHighLevelModelDataForDetailLevelGeneration,
    InitialBpmnDetailLevelValidation,
    MergeScopedDetailLevelModel,
    DetailLevelBpmnIRModelValidation,
    ResolveSyntheticComponents,
    ExecuteCopilotImpactAnalysis,
    UIGeneration,
    PrepareForRendering,
    ValidateBpmnModelCorrectness;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }

    public String description()
    {
        return switch (this) {
            case InitializeBpmnData -> "Initializing BPMN data";
            case InitializeBpmnPayload -> "Initializing BPMN payload";
            case InsertSyntheticComponents -> "Inserting synthetic components into the BPMN model";
            case ProcessHighLevelModelDataForDetailLevelGeneration -> "Processing high-level model data for detail-level generation";
            case InitialBpmnDetailLevelValidation -> "Performing initial validation of BPMN detail-level model";
            case MergeScopedDetailLevelModel -> "Merging scoped detail-level model";
            case DetailLevelBpmnIRModelValidation -> "Validating detail-level BPMN intermediate representation model";
            case ResolveSyntheticComponents -> "Resolving synthetic components in the BPMN model";
            case ExecuteCopilotImpactAnalysis -> "Executing Copilot impact analysis on the BPMN model";
            case UIGeneration -> "Generating user interface components for the BPMN model";
            case PrepareForRendering -> "Preparing BPMN model for rendering";
            case ValidateBpmnModelCorrectness -> "Validating BPMN model correctness";
        };
    }
}
