package org.rj.modelgen.llm.models.interpretation.model;

import org.rj.modelgen.llm.component.ComponentLibrary;
import org.rj.modelgen.llm.component.ComponentLibrarySerializer;
import org.rj.modelgen.llm.component.DefaultComponentLibrarySelector;
import org.rj.modelgen.llm.context.provider.ContextProvider;
import org.rj.modelgen.llm.intrep.ModelParser;
import org.rj.modelgen.llm.intrep.assets.ModelAssets;
import org.rj.modelgen.llm.intrep.core.model.IntermediateModel;
import org.rj.modelgen.llm.model.ModelInterface;
import org.rj.modelgen.llm.models.generation.multilevel.states.ReverseRenderFunction;
import org.rj.modelgen.llm.models.interpretation.data.InterpretationPayloadData;
import org.rj.modelgen.llm.models.interpretation.options.InterpretationModelOptions;
import org.rj.modelgen.llm.models.interpretation.prompt.InterpretationModelPromptGenerator;
import org.rj.modelgen.llm.models.interpretation.prompt.InterpretationModelPromptType;
import org.rj.modelgen.llm.models.interpretation.signals.InterpretationModelStandardSignals;
import org.rj.modelgen.llm.models.interpretation.states.*;
import org.rj.modelgen.llm.schema.ModelSchema;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.state.ModelInterfaceTransitionRule;
import org.rj.modelgen.llm.state.ModelInterfaceTransitionRules;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.rj.modelgen.llm.statemodel.states.common.impl.PrepareSpecificModelGenerationRequestPromptWithComponents;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.rj.modelgen.llm.models.interpretation.data.InterpretationPayloadData.ReverseRenderedIntermediateModel;
import static org.rj.modelgen.llm.models.interpretation.signals.InterpretationModelStandardSignals.SuccessfulLlmExecutionResponse;


public abstract class BaseInterpretationModel<TModel,
        TIntermediateModel extends IntermediateModel,
        TComponentLibrary extends ComponentLibrary<?>,
        TResult extends InterpretationResult,
        TModelAssets extends ModelAssets<?>>
        extends InterpretationModel<TResult> {

    public BaseInterpretationModel(Class<? extends BaseInterpretationModel<TModel, TIntermediateModel, TComponentLibrary, TResult, TModelAssets>> modelClass,
                                   ModelInterface modelInterface, InterpretationModelPromptGenerator promptGenerator,
                                   ComponentLibrarySerializer<TComponentLibrary> componentLibrarySerializer,
                                   ContextProvider contextProvider, TComponentLibrary componentLibrary,
                                   ReverseRenderFunction<TModel, TIntermediateModel, TModelAssets> reverseRenderFunction,
                                   ModelInterfaceState completionState,
                                   InterpretationModelOptions options,
                                   ModelParser<TModel> modelParser) {
        this(modelClass, modelInterface, buildModelData(promptGenerator, contextProvider, componentLibrary, reverseRenderFunction, completionState, options, componentLibrarySerializer, modelParser));
    }

    private BaseInterpretationModel(Class<? extends BaseInterpretationModel<TModel, TIntermediateModel, TComponentLibrary, TResult, TModelAssets>> modelClass,
                                    ModelInterface modelInterface, ModelData modelData) {
        super(modelClass, modelInterface, modelData.getStates(), modelData.getRules());
    }

    private static <TModel,
            TIntermediateModel extends IntermediateModel,
            TComponentLibrary extends ComponentLibrary<?>,
            TModelAssets extends ModelAssets<?>>
    ModelData buildModelData(
            InterpretationModelPromptGenerator promptGenerator,
            ContextProvider contextProvider, TComponentLibrary componentLibrary,
            ReverseRenderFunction<TModel, TIntermediateModel, TModelAssets> reverseRenderFunction,
            ModelInterfaceState completionState,
            InterpretationModelOptions options,
            ComponentLibrarySerializer<TComponentLibrary> componentLibrarySerializer,
            ModelParser<TModel> modelParser) {

        // Overrides from model options if any are provided
        final var modelOptions = Optional.ofNullable(options).orElseGet(InterpretationModelOptions::defaultOptions);
        final var modelPromptGenerator = modelOptions.applyPromptGeneratorCustomization(promptGenerator);
        final ModelSchema irSchema = Optional.ofNullable(modelOptions.getIntermediateRepresentationSchemaOverride()).orElse(new ModelSchema("{}"));

        // Build each model state
        final var stateInit = new StartInterpretation(StartInterpretation.class)
                .withOverriddenId(InterpretationStates.StartInterpretation);

        final var stateReverseRenderModeltoIR = (new ReverseRenderIntermediateModelFromModelTransformer<>(
                modelParser,
                StandardModelData.Request.toString(),
                ReverseRenderedIntermediateModel.toString(),
                reverseRenderFunction))
                .withOverriddenId(InterpretationStates.ReverseRender);

        // Preprocessing Step
        final var statePreprocessing = new PrepareIRModelForInterpretation()
                .withOverriddenId(InterpretationStates.PreProcessing);

        // LLM Steps

        // First Convert from the JSON IR to a step by step 'runbook' format, with labels from the action library
        final var statePrepareLLMInterpretIRModel = new PrepareSpecificModelGenerationRequestPromptWithComponents<>(irSchema, contextProvider, componentLibrary,
                new DefaultComponentLibrarySelector<>(),
                componentLibrarySerializer,
                modelPromptGenerator,
                InterpretationModelPromptType.InterpretIRModelToRunbookFormat)
                .withOverriddenId(InterpretationStates.PrepareRequestToInterpretIRModelToRunbookFormat);

        // Use a null sanitizer here as the output of the LLM call is textual
        final var stateSubmitLLMInterpretIRModel = new SubmitInterpretationRequestToLlm(null)
                .withResponseOutputKey(InterpretationPayloadData.InterpretationResult.toString())
                .withOverriddenId(InterpretationStates.SubmitRequestToInterpretIRModelToRunbookFormat);

        // Optionally, convert from this step by step format to a continuous prose description of the process
        /*
        final var stateLLMRunbookToProse = new PrepareAndSubmitLlmGenericRequest<>(contextProvider, modelPromptGenerator, InterpretationModelPromptType.InterpretRunbookToProse, componentLibrary)
                .withResponseOutputKey(InterpretationPayloadData.InterpretationResult)
                .withOverriddenId(InterpretationStates.InterpretRunbookToProse);
         */

        // Mechanical transformation step to go from one format to another (currently just removing a \n from the end of the rendered text)
        final var statePostProcessInterpretedResult = new PostProcessInterpretedModel()
                .withOverriddenId(InterpretationStates.RenderOutput);

        final var stateComplete = completionState
                .withOverriddenId(InterpretationStates.Complete);

        final var states = List.of(stateInit, stateReverseRenderModeltoIR, statePreprocessing, statePrepareLLMInterpretIRModel, stateSubmitLLMInterpretIRModel, statePostProcessInterpretedResult, stateComplete);

        // Complete initialization, and apply any global model state that the states want to consume
        states.forEach(ModelInterfaceState::completeStateInitialization);
        states.forEach(state -> state.applyModelOptions(options));

        // Transition rules between states
        final var rules = new ModelInterfaceTransitionRules(List.of(
                new ModelInterfaceTransitionRule(stateInit, StandardSignals.SUCCESS, stateReverseRenderModeltoIR),
                new ModelInterfaceTransitionRule(stateReverseRenderModeltoIR, StandardSignals.SUCCESS, statePreprocessing),

                new ModelInterfaceTransitionRule(statePreprocessing, StandardSignals.SUCCESS, statePrepareLLMInterpretIRModel),
                new ModelInterfaceTransitionRule(statePreprocessing, StandardSignals.SKIPPED, statePrepareLLMInterpretIRModel),  // Optional stage

                new ModelInterfaceTransitionRule(statePrepareLLMInterpretIRModel, StandardSignals.SUCCESS, stateSubmitLLMInterpretIRModel),
                new ModelInterfaceTransitionRule(stateSubmitLLMInterpretIRModel, SuccessfulLlmExecutionResponse, statePostProcessInterpretedResult),

                // Optional step to convert the runbook to prose
                // new ModelInterfaceTransitionRule(stateLLMRunbookToProse, StandardSignals.SUCCESS, statePostProcessInterpretedResult),

                new ModelInterfaceTransitionRule(statePostProcessInterpretedResult, StandardSignals.SUCCESS, stateComplete)
        ));

        return new ModelData(states, rules);
    }

    public abstract Mono<TResult> executeModel(String sessionId, String inputModel, String canvasModel, Map<String, Object> data);

    protected Class<? extends ModelInterfaceState> getInitialState() {
        return StartInterpretation.class;
    }

    protected InterpretationModelStandardSignals getStartSignal() {
        return InterpretationModelStandardSignals.StartInterpretation;
    }
}
