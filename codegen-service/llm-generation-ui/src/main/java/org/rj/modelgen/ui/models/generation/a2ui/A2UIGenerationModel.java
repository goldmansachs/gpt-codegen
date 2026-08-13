package org.rj.modelgen.ui.models.generation.a2ui;

import org.rj.modelgen.llm.context.provider.ContextProvider;
import org.rj.modelgen.llm.context.provider.impl.DefaultContextProvider;
import org.rj.modelgen.llm.model.ModelInterface;
import org.rj.modelgen.llm.state.ModelInterfaceExecutionResult;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.state.ModelInterfaceTransitionRule;
import org.rj.modelgen.llm.statemodel.signals.common.StandardErrorSignals;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.rj.modelgen.ui.component.A2UIComponentLibrary;
import org.rj.modelgen.ui.component.A2UIComponentLibrarySummarySerializer;
import org.rj.modelgen.ui.models.generation.UIGenerationModel;
import org.rj.modelgen.ui.models.generation.UIGenerationResult;
import org.rj.modelgen.ui.models.generation.UIGenerationTargetConfig;
import org.rj.modelgen.ui.models.generation.data.UIGenerationModelInputPayload;
import org.rj.modelgen.ui.models.generation.signals.UIGenerationSignals;
import org.rj.modelgen.ui.models.generation.states.PrepareAndSubmitA2UIConversionRequest;
import org.rj.modelgen.ui.models.generation.states.UIGenerationComplete;
import org.rj.modelgen.ui.models.generation.states.ValidateA2UIModelCorrectness;

import java.util.List;

/**
 * A2UI generation model that produces A2UI (Agent to UI) output from a user prompt.
 * Extends {@link UIGenerationModel} to provide A2UI-specific conversion and validation
 * stages on top of the generic UI generation pipeline (intent formalisation).
 *
 * <p><b>A2UI-specific stages</b> (provided by this subclass):</p>
 * <ol>
 *   <li><b>Convert to A2UI</b> — Transforms the structured intent description into valid A2UI
 *       Protocol v0.9 JSONL output, using the A2UI component library for structured schema
 *       injection into the prompt.</li>
 *   <li><b>Validate A2UI Output</b> — Validates the generated A2UI output against the A2UI schema
 *       and structural rules. If validation errors are found, feeds them back into the conversion
 *       stage for regeneration (up to a configured retry limit).</li>
 * </ol>
 *
 * <p>Full pipeline flow:</p>
 * <pre>
 * Start ──► ImpactAnalysis ──► ConvertToA2UI ──► ValidateOutput ──► Complete
 *              (base)               (A2UI)              (A2UI)
 *                                     ▲                    │
 *                                     │   (on failure)     │
 *                                     └────────────────────┘
 * </pre>
 */
public class A2UIGenerationModel extends UIGenerationModel<UIGenerationResult> {

    private static final int DEFAULT_VALIDATION_RETRY_LIMIT = 2;

    /**
     * Creates a new A2UI generation model with the given model interface and options.
     *
     * @param modelInterface    The LLM model interface used to submit generation requests
     * @param options           Model options (prompt overrides, LLM response overrides, etc.)
     * @return                  A fully-configured A2UI generation model
     */
    public static A2UIGenerationModel create(ModelInterface modelInterface, A2UIModelOptions options) {
        final var promptGenerator = new A2UIPromptGenerator();
        final var contextProvider = new DefaultContextProvider();
        final var targetConfig = buildA2UITargetConfig(promptGenerator, contextProvider);
        final var modelData = buildBaseModelData(promptGenerator, contextProvider, targetConfig, options,
                A2UIComponentLibrary.defaultLibrary(), new A2UIComponentLibrarySummarySerializer());

        return new A2UIGenerationModel(A2UIGenerationModel.class, modelInterface, modelData);
    }

    private A2UIGenerationModel(Class<? extends A2UIGenerationModel> modelClass,
                                ModelInterface modelInterface,
                                ModelData modelData) {
        super(modelClass, modelInterface, modelData);
    }

    /**
     * Builds the A2UI-specific target configuration containing the conversion and
     * validation states and their transition rules.
     */
    private static UIGenerationTargetConfig buildA2UITargetConfig(A2UIPromptGenerator promptGenerator,
                                                                  ContextProvider contextProvider) {
        // Convert to A2UI: transforms the user's intent into A2UI Protocol v0.9 JSONL.
        // Uses the A2UI component library for structured schema injection into the prompt.
        final var a2uiLibrary = A2UIComponentLibrary.defaultLibrary();

        final var stateConvertToA2UI = new PrepareAndSubmitA2UIConversionRequest(
                contextProvider, promptGenerator, A2UIPromptType.ConvertToA2UI, a2uiLibrary)
                .withResponseOutputKey(UIGenerationModelInputPayload.UI_OUTPUT)
                .withOverriddenId(A2UIStates.GenerateA2UI);

        // Validate Output: validates the generated A2UI output against the A2UI schema and
        // structural rules. Emits OutputValidated on success, or OutputValidationFailed on
        // failure (triggering re-generation up to the retry limit).
        final ModelInterfaceState stateValidateOutput;
        try {
            stateValidateOutput = new ValidateA2UIModelCorrectness()
                    .withOverriddenId(A2UIStates.ValidateOutput);
            stateValidateOutput.setInvokeLimit(DEFAULT_VALIDATION_RETRY_LIMIT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize A2UI schema validator", e);
        }

        final var targetStates = List.of(stateConvertToA2UI, stateValidateOutput);

        // --- A2UI-specific transition rules ---
        final var targetRules = List.of(
                // ConvertToA2UI -> ValidateOutput
                new ModelInterfaceTransitionRule(stateConvertToA2UI, StandardSignals.SUCCESS, stateValidateOutput),

                // ValidateOutput -> Complete (validation passed)
                new ModelInterfaceTransitionRule(stateValidateOutput, UIGenerationSignals.OutputValidated,
                        new UIGenerationComplete()),

                // ValidateOutput -> ConvertToA2UI (validation failed, feed errors back for regeneration)
                new ModelInterfaceTransitionRule(stateValidateOutput, UIGenerationSignals.OutputValidationFailed, stateConvertToA2UI),

                // ValidateOutput -> Complete (max retries exceeded, complete with best-effort output)
                new ModelInterfaceTransitionRule(stateValidateOutput, StandardErrorSignals.FAILED_MAX_INVOCATIONS,
                        new UIGenerationComplete())
        );

        return new UIGenerationTargetConfig(targetStates, targetRules);
    }

    @Override
    protected UIGenerationResult fromModelInterfaceExecutionResult(ModelInterfaceExecutionResult result) {
        return UIGenerationResult.fromModelExecutionResult(result);
    }

    public static A2UIModelOptions defaultOptions() {
        return A2UIModelOptions.defaultOptions();
    }
}
