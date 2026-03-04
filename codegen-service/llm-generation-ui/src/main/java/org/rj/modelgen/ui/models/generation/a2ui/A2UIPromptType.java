package org.rj.modelgen.ui.models.generation.pipeline;

import org.rj.modelgen.llm.util.StringSerializable;

/**
 * Prompt types used by each stage of the A2UI generation pipeline.
 */
public enum A2UIPipelinePromptType implements StringSerializable {
    SanitizeInput,
    FormaliseIntent,
    ConvertToA2UI;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }
}

