package org.rj.modelgen.llm.models.interpretation.model;

import org.rj.modelgen.llm.models.interpretation.states.InterpretationResult;
import org.rj.modelgen.llm.model.ModelInterface;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.state.ModelInterfaceStateMachine;
import org.rj.modelgen.llm.state.ModelInterfaceTransitionRules;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public abstract class InterpretationModel<TResult extends InterpretationResult> extends ModelInterfaceStateMachine {
    public InterpretationModel(Class<? extends ModelInterfaceStateMachine> modelClass, ModelInterface modelInterface, List<ModelInterfaceState> states, ModelInterfaceTransitionRules rules) {
        super(modelClass, modelInterface, states, rules);
    }

    /**
     * Implemented by subclasses to trigger execution of a specific model type
     *
     * @param sessionId     Current session
     * @param request       Input prompt
     * @param data          Initial input data to be passed into the model in the input signal
     * @return              Model execution result
     */
    public abstract Mono<TResult> executeModel(String sessionId, String request, String canvasModel, Map<String, Object> data);
}
