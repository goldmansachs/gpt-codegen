package org.rj.modelgen.ui.models.generation.a2ui;

import org.rj.modelgen.llm.util.StringSerializable;

/**
 * A2UI-specific prompt types. Generic prompt types (SanitizeInput, FormaliseIntent) are
 * defined in {@link org.rj.modelgen.ui.models.generation.UIGenerationModelPromptType}.
 */
public enum A2UIPromptType implements StringSerializable {
    ConvertToA2UI;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }
}
