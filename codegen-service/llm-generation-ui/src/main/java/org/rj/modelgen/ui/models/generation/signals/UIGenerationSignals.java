package org.rj.modelgen.ui.models.generation.signals;

import org.rj.modelgen.llm.util.StringSerializable;

/**
 * Signals used within the UI generation pipeline to drive transitions between states.
 *
 * <p>Generic pipeline transitions (Start → Sanitize → Formalise) use
 * {@link org.rj.modelgen.llm.statemodel.signals.common.StandardSignals#SUCCESS}.
 * The signals defined here cover output validation outcomes that are shared across
 * target-specific validation stages.</p>
 */
public enum UIGenerationSignals implements StringSerializable {
    OutputValidated,
    OutputValidationFailed,
    NoChangesRequired,
    InitialGenerationRequired,
    CopilotChangesRequired;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }
}
