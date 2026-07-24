package org.rj.modelgen.ui.models.generation;

import org.rj.modelgen.llm.util.StringSerializable;

/**
 * Prompt type used by the generic UI generation pipeline stages.
 * These are shared across all UI generation model implementations.
 *
 * <p>Target-specific prompt type (e.g. ConvertToA2UI) are defined by
 * each concrete subclass.</p>
 */
public enum UIGenerationModelPromptType implements StringSerializable {
    SanitizeInput,
    ImpactAnalysis,
    FormaliseIntent;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }
}
