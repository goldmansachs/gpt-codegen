package org.rj.modelgen.forms.models.generation.signals;

import org.rj.modelgen.llm.util.StringSerializable;

/**
 * Signals used within the form generation pipeline to drive transitions between states.
 */
public enum FormGenerationSignals implements StringSerializable {
    StartGeneration,
    InputSanitized,
    IntentFormalised,
    A2UIGenerated,
    OutputValidated,
    CompleteGeneration;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }
}

