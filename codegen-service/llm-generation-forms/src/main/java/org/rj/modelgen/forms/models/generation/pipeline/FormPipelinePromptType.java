package org.rj.modelgen.forms.models.generation.pipeline;

import org.rj.modelgen.llm.util.StringSerializable;

/**
 * Prompt types used by each stage of the form generation pipeline.
 */
public enum FormPipelinePromptType implements StringSerializable {
    SanitizeInput,
    FormaliseIntent,
    ConvertToA2UI;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }
}

