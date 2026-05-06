package org.rj.modelgen.llm.models.interpretation.states;

import org.rj.modelgen.llm.util.StringSerializable;

public enum InterpretationStates implements StringSerializable {
    StartInterpretation,
    ReverseRender,
    PreProcessing,
    PrepareRequestToInterpretIRModelToRunbookFormat,
    SubmitRequestToInterpretIRModelToRunbookFormat,
    InterpretRunbookToProse,
    RenderOutput,
    Complete;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }
}
