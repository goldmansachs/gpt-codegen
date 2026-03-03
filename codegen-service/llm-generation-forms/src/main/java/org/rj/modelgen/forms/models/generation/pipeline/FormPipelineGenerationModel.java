package org.rj.modelgen.forms.models.generation.pipeline;

import org.rj.modelgen.forms.component.A2UIComponentLibrary;
import org.rj.modelgen.forms.models.generation.FormGenerationExecutionModel;
import org.rj.modelgen.forms.models.generation.FormGenerationResult;
import org.rj.modelgen.forms.models.generation.data.FormGenerationModelInputPayload;
import org.rj.modelgen.forms.models.generation.signals.FormGenerationSignals;
import org.rj.modelgen.forms.models.generation.states.FormGenerationComplete;
import org.rj.modelgen.forms.models.generation.states.PrepareAndSubmitA2UIConversionRequest;
import org.rj.modelgen.forms.models.generation.states.StartFormGeneration;
import org.rj.modelgen.forms.models.generation.states.ValidateFormModelCorrectness;
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
 * Form generation pipeline model that produces an A2UI (Agent to UI) form from a user prompt.
 *
 * <p>The pipeline executes the following stages:</p>
 * <ol>
 *   <li><b>Sanitize Input</b> - Corrects spelling, grammar, and unclear phrasing in the user's
 *       request without altering meaning or intent. Simple text-to-text transformation.</li>
 *   <li><b>Formalise Intent</b> - Transforms the sanitized request into a structured description
 *       of form fields, including any fields that can be confidently inferred from the form type.
 *       Simple text-to-text transformation.</li>
 *   <li><b>Convert to A2UI</b> - Transforms the structured intent description into a valid A2UI
 *       Protocol v0.9 JSONL output conforming to the A2UI specification.</li>
 *   <li><b>Validate Output</b> - Validates the generated A2UI output against the A2UI schema and
 *       structural rules. If validation errors are found, feeds them back into the Convert to A2UI
 *       stage for regeneration (up to a configured retry limit).</li>
 * </ol>
 *
 * <p>Modelled after {@code BpmnMultiLevelGenerationModel}, but simplified to avoid concepts that
 * do not apply to form generation (e.g. subproblem decomposition, high-level/detail-level phases,
 * intermediate model transformation).</p>
 */
public class FormPipelineGenerationModel extends GenerationModel<FormGenerationResult>
        implements FormGenerationExecutionModel {

    private static final int DEFAULT_VALIDATION_RETRY_LIMIT = 2;

    /**
     * Creates a new form generation pipeline model with the given model interface and options.
     *
     * @param modelInterface    The LLM model interface used to submit generation requests
     * @param options           Pipeline model options (prompt overrides, LLM response overrides, etc.)
     * @return                  A fully-configured form generation pipeline model
     */
    public static FormPipelineGenerationModel create(ModelInterface modelInterface, FormPipelineModelOptions options) {
        final var promptGenerator = new FormPipelinePromptGenerator();
        final var contextProvider = new DefaultContextProvider();
        final var modelData = buildModelData(promptGenerator, contextProvider, options);

        return new FormPipelineGenerationModel(FormPipelineGenerationModel.class, modelInterface, modelData);
    }

    private FormPipelineGenerationModel(Class<? extends FormPipelineGenerationModel> modelClass,
                                        ModelInterface modelInterface,
                                        ModelData modelData) {
        super(modelClass, modelInterface, modelData.getStates(), modelData.getRules());
    }

    /**
     * Builds all pipeline states and transition rules for the form generation pipeline.
     *
     * <p>Pipeline flow:</p>
     * <pre>
     * Start -> SanitizeInput -> FormaliseIntent -> ConvertToA2UI -> ValidateOutput -> Complete
     *                                                  ^                |
     *                                                  |  (on failure)  |
     *                                                  +----------------+
     * </pre>
     */
    private static ModelData buildModelData(FormPipelinePromptGenerator promptGenerator,
                                            ContextProvider contextProvider,
                                            FormPipelineModelOptions options) {
        final var modelOptions = Optional.ofNullable(options).orElseGet(FormPipelineModelOptions::defaultOptions);
        final var modelPromptGenerator = modelOptions.applyPromptGeneratorCustomization(promptGenerator);

        // Am empty component library is used for the text-to-text stages (sanitize, formalise intent)
        // which do not require any component library context
        final var noOpLibrary = EmptyComponentLibrary.instance();

        // --- State definitions ---

        // 1. Start: Validate input and initialize session context
        final var stateStart = new StartFormGeneration()
                .withOverriddenId(FormPipelineStates.StartFormGeneration);

        // 2. Sanitize Input: Text-to-text transformation correcting spelling/grammar.
        //    Output is stored under SANITIZED_REQUEST in the payload for downstream stages.
        final var stateSanitizeInput = new PrepareAndSubmitLlmGenericRequest<>(
                contextProvider, modelPromptGenerator, FormPipelinePromptType.SanitizeInput, noOpLibrary)
                .withResponseOutputKey(FormGenerationModelInputPayload.SANITIZED_REQUEST)
                .withOverriddenId(FormPipelineStates.SanitizeInput);

        // 3. Formalise Intent: Text-to-text transformation producing structured field descriptions.
        //    Output is stored under FORMALISED_INTENT in the payload.
        final var stateFormaliseIntent = new PrepareAndSubmitLlmGenericRequest<>(
                contextProvider, modelPromptGenerator, FormPipelinePromptType.FormaliseIntent, noOpLibrary)
                .withResponseOutputKey(FormGenerationModelInputPayload.FORMALISED_INTENT)
                .withOverriddenId(FormPipelineStates.FormaliseIntent);

        // 4. Convert to A2UI: Transforms the formalised intent into A2UI Protocol v0.9 JSONL.
        //    Uses the A2UI component library for structured schema injection into the prompt.
        final var a2uiLibrary = A2UIComponentLibrary.defaultLibrary();



        final var stateConvertToA2UI = new PrepareAndSubmitA2UIConversionRequest(
                contextProvider, modelPromptGenerator, FormPipelinePromptType.ConvertToA2UI, a2uiLibrary)
                .withResponseOutputKey(FormGenerationModelInputPayload.A2UI_OUTPUT)
                .withOverriddenId(FormPipelineStates.ConvertToA2UI);

        // 5. Validate Output: Validates the generated A2UI output against schema and structural rules.
        //    Emits OutputValidated on success, OutputValidationFailed on failure (triggering re-generation).
        //    Has an invoke limit to prevent infinite validation loops.
        final ModelInterfaceState stateValidateOutput;
        try {
            stateValidateOutput = new ValidateFormModelCorrectness()
                    .withOverriddenId(FormPipelineStates.ValidateOutput);
            stateValidateOutput.setInvokeLimit(DEFAULT_VALIDATION_RETRY_LIMIT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize A2UI schema validator", e);
        }

        // 6. Complete: Terminal state capturing the final outputs (formalised intent, A2UI output,
        //    and any remaining validation messages)
        final var stateComplete = new FormGenerationComplete()
                .withOverriddenId(FormPipelineStates.Complete);

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
                new ModelInterfaceTransitionRule(stateValidateOutput, FormGenerationSignals.OutputValidated, stateComplete),

                // ValidateOutput -> ConvertToA2UI (validation failed, feed errors back for regeneration)
                new ModelInterfaceTransitionRule(stateValidateOutput, FormGenerationSignals.OutputValidationFailed, stateConvertToA2UI),

                // ValidateOutput -> Complete (max retries exceeded, complete with best-effort output)
                new ModelInterfaceTransitionRule(stateValidateOutput, StandardErrorSignals.FAILED_MAX_INVOCATIONS, stateComplete)
        ));

        return new ModelData(states, rules);
    }

    @Override
    public Mono<FormGenerationResult> executeModel(String sessionId, String request, Map<String, Object> data) {
        final var initialState = FormPipelineStates.StartFormGeneration.toString();

        final var input = new FormGenerationModelInputPayload(sessionId, request);
        if (data != null) input.putAllIfAbsent(data);

        return this.execute(initialState, FormGenerationSignals.StartGeneration, input)
                .map(FormGenerationResult::fromModelExecutionResult);
    }

    public static FormPipelineModelOptions defaultOptions() {
        return FormPipelineModelOptions.defaultOptions();
    }
}

