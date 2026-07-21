package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.generation.BpmnModelGenerationFunction;
import org.rj.modelgen.bpmn.generation.render.BpmnOriginalCanvas;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;
import org.rj.modelgen.bpmn.models.generation.multilevel.BpmnMultiLevelGenerationModel;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.state.ModelInterfaceStateMachine;
import org.rj.modelgen.llm.statemodel.states.common.impl.GenerateModelFromIntermediateModelTransformer;
import org.rj.modelgen.llm.util.Result;

import java.util.function.Function;

// BPMN-specific state that carries the original canvas layout into BpmnModelGenerationFunction, allowing for preservation of the canvas during copilot requests
// the canvas is null for forward gen. requests
public class BpmnGenerateModelFromIntermediateModel extends GenerateModelFromIntermediateModelTransformer<BpmnIntermediateModel, BpmnModelAssets, BpmnModelInstance> {

    private final BpmnModelGenerationFunction bpmnModelGenerationFunction;

    public BpmnGenerateModelFromIntermediateModel(String inputModelKey, String outputModelKey, BpmnModelGenerationFunction bpmnModelGenerationFunction, Function<BpmnModelInstance, String> renderedModelSerializer) {
        super(BpmnGenerateModelFromIntermediateModel.class, BpmnIntermediateModel.class, inputModelKey, outputModelKey, bpmnModelGenerationFunction, renderedModelSerializer);
        this.bpmnModelGenerationFunction = bpmnModelGenerationFunction;
    }

    @Override
    protected Result<BpmnModelInstance, String> generateModel(BpmnIntermediateModel intermediateModel, ModelInterfaceStateMachine executionModel) {
        recordAudit("prerender", intermediateModel.serialize());

        final BpmnOriginalCanvas canvas = getPayload().get(MultiLevelModelStandardPayloadData.OriginalCanvasLayout);
        final BpmnComponentLibrary componentLibrary = ((BpmnMultiLevelGenerationModel) executionModel).getComponentLibrary();

        return bpmnModelGenerationFunction.generateModel(intermediateModel, componentLibrary, canvas);
    }
}
