package org.rj.modelgen.bpmn.models.generation;

import org.rj.modelgen.bpmn.llm.context.provider.impl.ConstrainedBpmnGenerationContextProvider;
import org.rj.modelgen.bpmn.models.generation.context.BpmnGenerationPromptGenerator;
import org.rj.modelgen.bpmn.models.generation.signals.*;
import org.rj.modelgen.bpmn.models.generation.states.*;
import org.rj.modelgen.llm.client.LlmClient;
import org.rj.modelgen.llm.client.LlmClientImpl;
import org.rj.modelgen.llm.integrations.openai.OpenAIClientConfig;
import org.rj.modelgen.llm.model.ModelInterface;
import org.rj.modelgen.llm.schema.ModelSchema;
import org.rj.modelgen.llm.state.*;
import org.rj.modelgen.llm.util.Util;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

public class BpmnGenerationExecutionModel extends ModelInterfaceStateMachine {
    public static BpmnGenerationExecutionModel create(ModelInterface modelInterface, ModelSchema modelSchema) {
        final var promptGenerator = BpmnGenerationPromptGenerator.create(
                Util.loadStringResource("content/bpmn-prompt-template"),
                "<not-implemented>",
                "<not-implemented>"
        );

        // Build model states
        final var stateInit = new StartBpmnGeneration();
        final var statePrepareRequest = new PrepareBpmnModelGenerationRequest(modelSchema, promptGenerator);
        final var stateSubmitToLlm = new SubmitBpmnGenerationRequestToLlm();
        final var stateValidateLlmResponse = new ValidateLlmIntermediateModelResponse();
        final var stateGenerateBpmnXml = new GenerateBpmnXmlFromIntermediateRepresentation();
        final var stateValidateBpmnModelCorrectness = new ValidateBpmnModelCorrectness();
        final var stateComplete = new BpmnGenerationComplete();

        final var states = List.of(stateInit, statePrepareRequest, stateSubmitToLlm, stateValidateLlmResponse,
                                   stateGenerateBpmnXml, stateValidateBpmnModelCorrectness, stateComplete);

        // Define transition rules between states
        final var rules = new ModelInterfaceTransitionRules(List.of(
                new ModelInterfaceTransitionRule<>(stateInit, NewBpmnGenerationRequestReceived.class, statePrepareRequest),
                new ModelInterfaceTransitionRule<>(statePrepareRequest, LlmModelRequestPreparedSuccessfully.class, stateSubmitToLlm),
                new ModelInterfaceTransitionRule<>(stateSubmitToLlm, LlmResponseReceived.class, stateValidateLlmResponse),
                new ModelInterfaceTransitionRule<>(stateValidateLlmResponse, LlmResponseModelDataIsValid.class, stateGenerateBpmnXml),
                new ModelInterfaceTransitionRule<>(stateGenerateBpmnXml, BpmnXmlSuccessfullyGeneratedFromModelResponse.class, stateValidateBpmnModelCorrectness),
                new ModelInterfaceTransitionRule<>(stateValidateBpmnModelCorrectness, BpmnXmlDataPassedValidation.class, stateComplete)
        ));

        return new BpmnGenerationExecutionModel(modelInterface, states, rules);
    }

    private BpmnGenerationExecutionModel(ModelInterface modelInterface, List<ModelInterfaceState<? extends ModelInterfaceSignal>> states,
                                         ModelInterfaceTransitionRules rules) {
        super(modelInterface, states, rules);
    }

    public Mono<BpmnGenerationResult> executeModel(String sessionId, String request) {
        final var initialState = ModelInterfaceState.defaultStateId(StartBpmnGeneration.class);
        final var startSignal = new StartBpmnGenerationSignal(request, sessionId);

        return this.execute(initialState, startSignal)
                .map(BpmnGenerationResult::fromModelExecutionResult);
    }
}
