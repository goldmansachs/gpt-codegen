package org.rj.modelgen.ui.models.generation.pipeline;

import org.rj.modelgen.ui.component.A2UIComponentLibrary;
import org.rj.modelgen.ui.models.generation.UIGenerationExecutionModel;
import org.rj.modelgen.ui.models.generation.UIGenerationResult;
import org.rj.modelgen.ui.models.generation.data.UIGenerationModelInputPayload;
import org.rj.modelgen.ui.models.generation.signals.UIGenerationSignals;
import org.rj.modelgen.ui.models.generation.states.UIGenerationComplete;
import org.rj.modelgen.ui.models.generation.states.PrepareAndSubmitA2UIConversionRequest;
import org.rj.modelgen.ui.models.generation.states.StartUIGeneration;
import org.rj.modelgen.ui.models.generation.states.ValidateA2UIModelCorrectness;
import org.rj.modelgen.llm.context.provider.ContextProvider;
import org.rj.modelgen.llm.context.provider.impl.DefaultContextProvider;
import org.rj.modelgen.llm.model.ModelInterface;
import org.rj.modelgen.llm.models.generation.GenerationModel;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.state.ModelInterfaceTransitionRule;
import org.rj.modelgen.llm.state.ModelInterfaceTransitionRules;
import org.rj.modelgen.llm.statemodel.signals.common.StandardErrorSignals;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.rj.modelgen.llm.statemodel.states.common.PrepareAndSubmitLlmGenericRequest;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A2UI generation pipeline model that produces A2UI (Agent to UI) output from a user prompt.
 * Implements the {@link UIGenerationExecutionModel} interface, providing A2UI Generation as
 * a concrete capability of the generic UI generation framework.
 *
 * <p>The pipeline executes the following stages, split between generic UI generation
 * concerns and A2UI-specific processing:</p>
 *
 * <p><b>Generic UI Generation stages</b> (input sanitization and intent generation):</p>
 * <ol>
 *   <li><b>Sanitize Input</b> — Corrects spelling, grammar, and unclear phrasing in the user's
 *       request without altering meaning or intent. Generic text-to-text transformation shared
 *       by all UI generation pipelines.</li>
 *   <li><b>Formalise Intent</b> — Transforms the sanitized request into a structured description
 *       of UI elements, including any elements that can be confidently inferred from the request.
 *       Generic text-to-text transformation shared by all UI generation pipelines.</li>
 * </ol>
 *
 * <p><b>A2UI-specific stages</b> (conversion and validation):</p>
 * <ol start="3">
 *   <li><b>Convert to A2UI</b> — Transforms the structured intent description into valid A2UI
 *       Protocol v0.9 JSONL output, using the A2UI component library for structured schema
 *       injection into the prompt.</li>
 *   <li><b>Validate A2UI Output</b> — Validates the generated A2UI output against the A2UI schema
 *       and structural rules. If validation errors are found, feeds them back into the conversion
 *       stage for regeneration (up to a configured retry limit).</li>
 * </ol>
 *
 * <p>Pipeline flow:</p>
 * <pre>
 * Start ──► SanitizeInput ──► FormaliseIntent ──► ConvertToA2UI ──► ValidateOutput ──► Complete
 *           (UI generic)      (UI generic)        (A2UI-specific)       (A2UI-specific)
 *                                                       ▲                    │
 *                                                       │   (on failure)     │
 *                                                       └────────────────────┘
 * </pre>
 */
public class A2UIPipelineGenerationModel extends GenerationModel<UIGenerationResult>
        implements UIGenerationExecutionModel {

    private static final int DEFAULT_VALIDATION_RETRY_LIMIT = 2;

    /**
     * Creates a new A2UI generation pipeline model with the given model interface and options.
     *
     * @param modelInterface    The LLM model interface used to submit generation requests
     * @param options           Pipeline model options (prompt overrides, LLM response overrides, etc.)
     * @return                  A fully-configured A2UI generation pipeline model
     */
    public static A2UIPipelineGenerationModel create(ModelInterface modelInterface, A2UIPipelineModelOptions options) {
        final var promptGenerator = new A2UIPipelinePromptGenerator();
        final var contextProvider = new DefaultContextProvider();
        final var modelData = buildModelData(promptGenerator, contextProvider, options);

        return new A2UIPipelineGenerationModel(A2UIPipelineGenerationModel.class, modelInterface, modelData);
    }

    private A2UIPipelineGenerationModel(Class<? extends A2UIPipelineGenerationModel> modelClass,
                                        ModelInterface modelInterface,
                                        ModelData modelData) {
        super(modelClass, modelInterface, modelData.getStates(), modelData.getRules());
    }

    /**
     * Builds all pipeline states and transition rules for the A2UI generation pipeline.
     * Constructs both the generic UI generation stages (sanitization and intent formalisation)
     * and the A2UI-specific stages (conversion and validation).
     */
    private static ModelData buildModelData(A2UIPipelinePromptGenerator promptGenerator,
                                            ContextProvider contextProvider,
                                            A2UIPipelineModelOptions options) {
        final var modelOptions = Optional.ofNullable(options).orElseGet(A2UIPipelineModelOptions::defaultOptions);
        final var modelPromptGenerator = modelOptions.applyPromptGeneratorCustomization(promptGenerator);

        // An empty component library is used for the generic UI generation stages (sanitize, formalise
        // intent) which are text-to-text transformations and do not require any component library context
        final var noOpLibrary = EmptyComponentLibrary.instance();

        // --- Generic UI Generation states (sanitization and intent generation) ---

        // 1. Start: Validate input and initialize session context
        final var stateStart = new StartUIGeneration()
                .withOverriddenId(A2UIPipelineStates.StartUIGeneration);

        // 2. Sanitize Input: Generic UI generation stage — text-to-text transformation correcting
        //    spelling/grammar. Output stored under SANITIZED_REQUEST for downstream stages.
        final var stateSanitizeInput = new PrepareAndSubmitLlmGenericRequest<>(
                contextProvider, modelPromptGenerator, A2UIPipelinePromptType.SanitizeInput, noOpLibrary)
                .withResponseOutputKey(UIGenerationModelInputPayload.SANITIZED_REQUEST)
                .withOverriddenId(A2UIPipelineStates.SanitizeInput);

        // 3. Formalise Intent: Generic UI generation stage — text-to-text transformation producing
        //    a structured description of UI elements. Output stored under FORMALISED_INTENT.
        final var stateFormaliseIntent = new PrepareAndSubmitLlmGenericRequest<>(
                contextProvider, modelPromptGenerator, A2UIPipelinePromptType.FormaliseIntent, noOpLibrary)
                .withResponseOutputKey(UIGenerationModelInputPayload.FORMALISED_INTENT)
                .withOverriddenId(A2UIPipelineStates.FormaliseIntent);

        // --- A2UI-specific states (conversion and validation) ---

        // 4. Convert to A2UI: A2UI-specific stage — transforms the formalised intent into A2UI
        //    Protocol v0.9 JSONL. Uses the A2UI component library for structured schema injection.
        final var a2uiLibrary = A2UIComponentLibrary.defaultLibrary();

        final var stateConvertToA2UI = new PrepareAndSubmitA2UIConversionRequest(
                contextProvider, modelPromptGenerator, A2UIPipelinePromptType.ConvertToA2UI, a2uiLibrary)
                .withResponseOutputKey(UIGenerationModelInputPayload.A2UI_OUTPUT)
                .withOverriddenId(A2UIPipelineStates.ConvertToA2UI);

        // 5. Validate Output: A2UI-specific stage — validates the generated A2UI output against
        //    the A2UI schema and structural rules. Emits OutputValidated on success, or
        //    OutputValidationFailed on failure (triggering re-generation up to the retry limit).
        final ModelInterfaceState stateValidateOutput;
        try {
            stateValidateOutput = new ValidateA2UIModelCorrectness()
                    .withOverriddenId(A2UIPipelineStates.ValidateOutput);
            stateValidateOutput.setInvokeLimit(DEFAULT_VALIDATION_RETRY_LIMIT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize A2UI schema validator", e);
        }

        // --- Terminal state ---

        // 6. Complete: Captures the final outputs (formalised intent, A2UI output, and any
        //    remaining validation messages)
        final var stateComplete = new UIGenerationComplete()
                .withOverriddenId(A2UIPipelineStates.Complete);

        final var states = List.of(stateStart, stateSanitizeInput, stateFormaliseIntent,
                stateConvertToA2UI, stateValidateOutput, stateComplete);

        // Complete initialization and apply model options to all states
        states.forEach(ModelInterfaceState::completeStateInitialization);
        states.forEach(state -> state.applyModelOptions(modelOptions));

        // --- Transition rules ---
        final var rules = new ModelInterfaceTransitionRules(List.of(
                // Start -> SanitizeInput
                new ModelInterfaceTransitionRule(stateStart, StandardSignals.SUCCESS, stateSanitizeInput),

                // SanitizeInput -> FormaliseIntent (or skip if no sanitize prompt defined)
                new ModelInterfaceTransitionRule(stateSanitizeInput, StandardSignals.SUCCESS, stateFormaliseIntent),
                new ModelInterfaceTransitionRule(stateSanitizeInput, StandardSignals.SKIPPED, stateFormaliseIntent),

                // FormaliseIntent -> ConvertToA2UI (or skip if no formalise prompt defined)
                new ModelInterfaceTransitionRule(stateFormaliseIntent, StandardSignals.SUCCESS, stateConvertToA2UI),
                new ModelInterfaceTransitionRule(stateFormaliseIntent, StandardSignals.SKIPPED, stateConvertToA2UI),

                // ConvertToA2UI -> ValidateOutput
                new ModelInterfaceTransitionRule(stateConvertToA2UI, StandardSignals.SUCCESS, stateValidateOutput),

                // ValidateOutput -> Complete (validation passed, no errors found)
                new ModelInterfaceTransitionRule(stateValidateOutput, UIGenerationSignals.OutputValidated, stateComplete),

                // ValidateOutput -> ConvertToA2UI (validation failed, feed errors back for regeneration)
                new ModelInterfaceTransitionRule(stateValidateOutput, UIGenerationSignals.OutputValidationFailed, stateConvertToA2UI),

                // ValidateOutput -> Complete (max retries exceeded, complete with best-effort output)
                new ModelInterfaceTransitionRule(stateValidateOutput, StandardErrorSignals.FAILED_MAX_INVOCATIONS, stateComplete)
        ));

        return new ModelData(states, rules);
    }

    @Override
    public Mono<UIGenerationResult> executeModel(String sessionId, String request, Map<String, Object> data) {
        final var initialState = A2UIPipelineStates.StartUIGeneration.toString();

        final var input = new UIGenerationModelInputPayload(sessionId, request);
        if (data != null) input.putAllIfAbsent(data);

        return this.execute(initialState, UIGenerationSignals.StartGeneration, input)
                .map(UIGenerationResult::fromModelExecutionResult);
    }

    public static A2UIPipelineModelOptions defaultOptions() {
        return A2UIPipelineModelOptions.defaultOptions();
    }
}
