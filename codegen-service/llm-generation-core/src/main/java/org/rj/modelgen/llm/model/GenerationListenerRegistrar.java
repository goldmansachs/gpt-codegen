package org.rj.modelgen.llm.model;

import org.rj.modelgen.llm.state.ModelInterfaceStateMachine;

public interface GenerationListenerRegistrar {
    void register(ModelInterfaceStateMachine model);
}
