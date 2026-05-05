package org.rj.modelgen.bpmn.interpretation;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.BpmnComponentLibraryDetailLevelSerializer;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.generation.BpmnReverseRenderFunction;
import org.rj.modelgen.bpmn.interpretation.data.BpmnInterpretationComplete;
import org.rj.modelgen.bpmn.interpretation.data.BpmnModelInterpretationResult;
import org.rj.modelgen.bpmn.interpretation.prompt.BpmnInterpretationPromptGenerator;
import org.rj.modelgen.bpmn.intrep.BpmnModelParser;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.models.generation.base.data.BpmnGenerationModelInputPayload;
import org.rj.modelgen.llm.context.provider.ContextProvider;
import org.rj.modelgen.llm.context.provider.impl.DefaultContextProvider;
import org.rj.modelgen.llm.model.ModelInterface;
import org.rj.modelgen.llm.models.generation.multilevel.states.ReverseRenderFunction;
import org.rj.modelgen.llm.models.interpretation.model.BaseInterpretationModel;
import org.rj.modelgen.llm.models.interpretation.options.InterpretationModelOptions;
import org.rj.modelgen.llm.models.interpretation.prompt.InterpretationModelPromptGenerator;
import org.rj.modelgen.llm.models.interpretation.states.InterpretationStates;
import org.rj.modelgen.llm.schema.ModelSchema;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.state.ModelInterfaceStateMachineCustomization;
import org.rj.modelgen.llm.util.Util;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class BpmnInterpretationModel extends BaseInterpretationModel<BpmnModelInstance, BpmnIntermediateModel,
        BpmnComponentLibrary, BpmnModelInterpretationResult> {

    private BpmnInterpretationModel(ModelInterface modelInterface, InterpretationModelPromptGenerator promptGenerator, ContextProvider contextProvider, BpmnComponentLibrary componentLibrary, ReverseRenderFunction<BpmnModelInstance, BpmnIntermediateModel> reverseRenderFunction, ModelInterfaceState completionState, InterpretationModelOptions options, BpmnModelParser modelParser) {
        super(BpmnInterpretationModel.class, modelInterface, promptGenerator, new BpmnComponentLibraryDetailLevelSerializer(), contextProvider, componentLibrary, reverseRenderFunction, completionState, options, modelParser);
    }

    public static BpmnInterpretationModel create(ModelInterface modelInterface, InterpretationModelOptions options, String namespaceUri) {
        final var promptGenerator = new BpmnInterpretationPromptGenerator();
        final var contextProvider = new DefaultContextProvider();
        final var componentLibrary = BpmnComponentLibrary.defaultLibrary();
        final var reverseRenderFunction = new BpmnReverseRenderFunction(componentLibrary, BpmnGlobalVariableLibrary.defaultLibrary(), namespaceUri);
        final var modelParser = new BpmnModelParser();

        final var completionState = new BpmnInterpretationComplete();

        return (BpmnInterpretationModel) new BpmnInterpretationModel(
                modelInterface, promptGenerator, contextProvider, componentLibrary, reverseRenderFunction, completionState,
                options.withIntermediateRepresentationSchemaOverride(new ModelSchema(Util.loadStringResource("content/models/multilevel/bpmn-multilevel-detail-level-generation-schema.json"))), modelParser)
                .withModelCustomization(BpmnInterpretationModel::addBpmnModelCustomization);
    }

    private static ModelInterfaceStateMachineCustomization addBpmnModelCustomization(ModelCustomizationData modelData) {
        final List<BiFunction<ModelInterfaceStateMachineCustomization, ModelCustomizationData, ModelInterfaceStateMachineCustomization>> customizations = List.of();

        // Apply each customization in turn and return the full result
        return customizations.stream()
                .reduce(new ModelInterfaceStateMachineCustomization(),
                        (customization, f) -> f.apply(customization, modelData),
                        (a, b) -> a);
    }

    public Mono<BpmnModelInterpretationResult> executeModel(String sessionId, String inputModel, String canvasModel, Map<String, Object> data) {
        final var initialState = InterpretationStates.StartInterpretation.toString();

        BpmnGenerationModelInputPayload input = new BpmnGenerationModelInputPayload(
                sessionId, inputModel, canvasModel);
        input.putAll(data);

        return this.execute(initialState, getStartSignal(), input)
                .map(BpmnModelInterpretationResult::fromModelExecutionResult);
    }
}
