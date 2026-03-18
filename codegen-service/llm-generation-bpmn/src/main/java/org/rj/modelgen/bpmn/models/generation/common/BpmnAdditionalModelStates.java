package org.rj.modelgen.bpmn.models.generation.common;

import org.rj.modelgen.llm.util.StringSerializable;

public enum BpmnAdditionalModelStates implements StringSerializable {
    InitializeBpmnData,
    InitializeBpmnPayload,
    InsertSyntheticComponents,
    ProcessHighLevelModelDataForDetailLevelGeneration,
    InitialBpmnDetailLevelValidation,
    DetailLevelBpmnIRModelValidation,
    ResolveSyntheticComponents,
    UIGeneration,
    PrepareForRendering,
    ValidateBpmnModelCorrectness;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }
}
