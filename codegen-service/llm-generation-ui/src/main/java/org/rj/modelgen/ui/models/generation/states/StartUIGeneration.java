package org.rj.modelgen.ui.models.generation.states;

import org.rj.modelgen.llm.statemodel.states.common.StartGeneration;

/**
 * Initial state for the UI generation pipeline.
 * Validates input data and initializes the session context.
 */
public class StartUIGeneration extends StartGeneration {
    public StartUIGeneration() {
        super(StartUIGeneration.class);
    }

    @Override
    public String getDescription() {
        return "Begin UI generation pipeline";
    }

}
