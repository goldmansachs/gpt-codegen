package org.rj.modelgen.llm.models.interpretation.prompt;

import org.rj.modelgen.llm.util.StringSerializable;

public enum InterpretationModelPromptType implements StringSerializable {

    SanitizingPrePass,
    PreProcessing,
    InterpretIRModelToRunbookFormat,
    InterpretRunbookToProse,
    CorrectInterpretedModelErrors,
    PostProcessing;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }
}
