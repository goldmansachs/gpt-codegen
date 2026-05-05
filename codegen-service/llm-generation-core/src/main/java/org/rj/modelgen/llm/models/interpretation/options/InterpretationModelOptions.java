package org.rj.modelgen.llm.models.interpretation.options;

import org.rj.modelgen.llm.models.generation.options.GenerationModelOptionsImpl;
import org.rj.modelgen.llm.schema.ModelSchema;

public class InterpretationModelOptions extends GenerationModelOptionsImpl<InterpretationModelOptions> {
    private ModelSchema intermediateRepresentationSchemaOverride;

    private InterpretationModelOptions() { }

    public static InterpretationModelOptions defaultOptions() {
        return new InterpretationModelOptions();
    }

    public ModelSchema getIntermediateRepresentationSchemaOverride() {
        return intermediateRepresentationSchemaOverride;
    }

    public void setIntermediateRepresentationSchemaOverride(ModelSchema intermediateRepresentationSchemaOverride) {
        this.intermediateRepresentationSchemaOverride = intermediateRepresentationSchemaOverride;
    }

    public InterpretationModelOptions withIntermediateRepresentationSchemaOverride(ModelSchema intermediateRepresentationSchemaOverride) {
        setIntermediateRepresentationSchemaOverride(intermediateRepresentationSchemaOverride);
        return this;
    }
}
