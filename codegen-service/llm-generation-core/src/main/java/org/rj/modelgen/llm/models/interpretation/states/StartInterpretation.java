package org.rj.modelgen.llm.models.interpretation.states;

import org.rj.modelgen.llm.exception.LlmGenerationModelException;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.statemodel.signals.common.CommonStateInterface;
import reactor.core.publisher.Mono;

public class StartInterpretation extends ModelInterfaceState implements CommonStateInterface {
    public StartInterpretation(Class<? extends StartInterpretation> cls) {
        super(cls);
    }

    @Override
    public String getDescription() {
        return "Begin interpretation process";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal input) {
        if (!getPayload().hasData(StandardModelData.Request)) return error("Interpretation request is missing input request data");

        final String sessionId = getPayload().getOrThrow(StandardModelData.SessionId, () -> new LlmGenerationModelException("No valid session ID"));
        final var session = getModelInterface().getOrCreateSession(sessionId);

        return outboundSignal(getSuccessSignalId())
                .withPayloadData(StandardModelData.SessionId, session.getId())
                .withPayloadData(StandardModelData.Context, session.getContext())
                .mono();
    }
}
