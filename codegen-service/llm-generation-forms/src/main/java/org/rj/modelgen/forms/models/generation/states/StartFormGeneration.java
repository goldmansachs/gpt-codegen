package org.rj.modelgen.forms.models.generation.states;

import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.rj.modelgen.llm.statemodel.states.common.StartGeneration;

/**
 * Initial state for the form generation pipeline.
 * Validates input data and initializes the session context.
 */
public class StartFormGeneration extends StartGeneration {
    public StartFormGeneration() {
        super(StartFormGeneration.class);
    }

    @Override
    public String getDescription() {
        return "Begin form generation pipeline";
    }

    @Override
    public String getSuccessSignalId() {
        return StandardSignals.SUCCESS;
    }
}

