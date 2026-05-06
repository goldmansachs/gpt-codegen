package org.rj.modelgen.llm.models.interpretation.data;

public enum InterpretationPayloadData {
    InterpretationResult,
    ReverseRenderedIntermediateModel,
    ValidationMessages;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }
}
