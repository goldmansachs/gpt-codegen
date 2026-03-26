package org.rj.modelgen.ui.models.generation;

import org.rj.modelgen.llm.context.provider.ContextProvider;
import org.rj.modelgen.llm.model.ModelInterface;
import org.rj.modelgen.llm.models.generation.GenerationModel;
import org.rj.modelgen.llm.models.generation.GenerationResult;
import org.rj.modelgen.llm.state.ModelInterfaceExecutionResult;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.state.ModelInterfaceTransitionRule;
import org.rj.modelgen.llm.state.ModelInterfaceTransitionRules;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.rj.modelgen.llm.statemodel.states.common.PrepareAndSubmitLlmGenericRequest;
import org.rj.modelgen.ui.models.generation.data.UIGenerationModelInputPayload;
import org.rj.modelgen.ui.models.generation.states.StartUIGeneration;
import org.rj.modelgen.ui.models.generation.states.UIGenerationComplete;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for UI generation models.
 * Implementations execute the UI generation which sanitizes input,
 * formalises intent, and converts to the target UI format.
 */
public abstract class UIGenerationModel<R extends GenerationResult> extends GenerationModel<R> {

    protected UIGenerationModel(Class<? extends UIGenerationModel<?>> modelClass,
                                ModelInterface modelInterface,
                                ModelData modelData) {
        super(modelClass, modelInterface, modelData.getStates(), modelData.getRules());
    }

    /**
     * Builds the complete pipeline model data by combining the generic UI generation
     * stages (sanitize, formalise intent) with the target-specific stages provided
     * by the subclass via {@link UIGenerationTargetConfig}.
     *
     * @param promptGenerator   the prompt generator containing templates for sanitize and formalise intent
     * @param contextProvider   provides and manages conversation context
     * @param targetConfig      target-specific states and transition rules from the subclass
     * @param options           model options (prompt overrides, etc.)
     * @return                  combined model data with all states and transition rules
     */
    protected static ModelData buildBaseModelData(UIGenerationPromptGenerator promptGenerator,
                                                  ContextProvider contextProvider,
                                                  UIGenerationTargetConfig targetConfig,
                                                  UIGenerationModelOptions<?> options) {
        final var modelOptions = Optional.ofNullable(options).orElseGet(UIGenerationModelOptions::defaultOptions);
        final var modelPromptGenerator = modelOptions.applyPromptGeneratorCustomization(promptGenerator);

        // An empty component library is used for the generic UI generation stages (sanitize, formalise
        // intent) which are text-to-text transformations and do not require any component library context
        final var noOpLibrary = EmptyComponentLibrary.instance();

        // --- Generic UI Generation states (sanitization and intent generation) ---

        // 1. Start: Validate input and initialize session context
        final var stateStart = new StartUIGeneration()
                .withOverriddenId(UIGenerationModelStates.StartUIGeneration);

        // 2. Sanitize Input: Generic UI generation stage — text-to-text transformation correcting
        //    spelling/grammar. Output stored under SANITIZED_REQUEST for downstream stages.
        final var stateSanitizeInput = new PrepareAndSubmitLlmGenericRequest<>(
                contextProvider, modelPromptGenerator, UIGenerationModelPromptType.SanitizeInput, noOpLibrary)
                .withResponseOutputKey(UIGenerationModelInputPayload.SANITIZED_REQUEST)
                .withOverriddenId(UIGenerationModelStates.SanitizeInput);

        // 3. Formalise Intent: Generic UI generation stage — transforms the sanitized request into
        //    a structured description of UI elements.
        final var stateFormaliseIntent = new PrepareAndSubmitLlmGenericRequest<>(
                contextProvider, modelPromptGenerator, UIGenerationModelPromptType.FormaliseIntent, noOpLibrary)
                .withResponseOutputKey(UIGenerationModelInputPayload.FORMALISED_INTENT)
                .withOverriddenId(UIGenerationModelStates.FormaliseIntent);

        // Terminal: Captures the final outputs from the pipeline stages
        final var stateComplete = new UIGenerationComplete()
                .withOverriddenId(UIGenerationModelStates.Complete);

        // --- Combine generic states with target-specific states ---
        final var allStates = new ArrayList<ModelInterfaceState>();
        allStates.addAll(List.of(stateStart, stateSanitizeInput, stateFormaliseIntent));
        allStates.addAll(targetConfig.getTargetStates());
        allStates.add(stateComplete);

        // Complete initialization and apply model options
        allStates.forEach(ModelInterfaceState::completeStateInitialization);
        allStates.forEach(state -> state.applyModelOptions(modelOptions));

        // The first target state is the entry point from the generic pipeline into the target-specific pipeline
        final var firstTargetState = targetConfig.getTargetStates().get(0);

        // --- Generic transition rules ---
        final var allRules = new ArrayList<ModelInterfaceTransitionRule>();
        allRules.addAll(List.of(
                new ModelInterfaceTransitionRule(stateStart, StandardSignals.SUCCESS, stateSanitizeInput),
                new ModelInterfaceTransitionRule(stateSanitizeInput, StandardSignals.SUCCESS, stateFormaliseIntent),
                new ModelInterfaceTransitionRule(stateFormaliseIntent, StandardSignals.SUCCESS, firstTargetState)
        ));

        // Add target-specific transition rules, resolving any UIGenerationComplete placeholder
        // targets to the shared terminal state created above
        allRules.addAll(targetConfig.resolveTargetRules(stateComplete));

        return new ModelData(allStates, new ModelInterfaceTransitionRules(allRules));
    }

    protected abstract R fromModelInterfaceExecutionResult(ModelInterfaceExecutionResult result);

    @Override
    public Mono<R> executeModel(String sessionId, String request, String canvasaModel, Map<String, Object> data) {
        final var initialState = UIGenerationModelStates.StartUIGeneration.toString();

        UIGenerationModelInputPayload input = new UIGenerationModelInputPayload(sessionId, request);
        if (data != null) input.putAllIfAbsent(data);

        return this.execute(initialState, StandardSignals.SUCCESS, input)
                .map(this::fromModelInterfaceExecutionResult);
    }
}
