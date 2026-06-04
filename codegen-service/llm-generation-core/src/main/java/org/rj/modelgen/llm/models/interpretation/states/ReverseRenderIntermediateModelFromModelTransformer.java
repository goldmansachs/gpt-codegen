package org.rj.modelgen.llm.models.interpretation.states;

import org.rj.modelgen.llm.intrep.ModelParser;
import org.rj.modelgen.llm.intrep.assets.ModelAssets;
import org.rj.modelgen.llm.intrep.core.model.IntermediateModel;
import org.rj.modelgen.llm.models.generation.multilevel.states.ReverseRenderFunction;
import org.rj.modelgen.llm.models.interpretation.data.InterpretationData;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.util.Result;
import reactor.core.publisher.Mono;

public class ReverseRenderIntermediateModelFromModelTransformer<TModel, TIntermediateModel extends IntermediateModel, TModelAssets extends ModelAssets<?>> extends ModelInterfaceState {
    private final ReverseRenderFunction<TModel, TIntermediateModel, TModelAssets> reverseRenderFunction;
    private final ModelParser<TModel> modelParser;

    private final String inputModelKey;
    private final String outputModelKey;

    public ReverseRenderIntermediateModelFromModelTransformer(ModelParser<TModel> modelParser, String inputModelKey, String outputModelKey, ReverseRenderFunction<TModel, TIntermediateModel, TModelAssets> reverseRenderFunction) {
        super(ReverseRenderIntermediateModelFromModelTransformer.class);

        this.modelParser = modelParser;
        this.reverseRenderFunction = reverseRenderFunction;
        this.inputModelKey = inputModelKey;
        this.outputModelKey = outputModelKey;
    }

    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal inputSignal) {
        String inputContent;

        if(this.getPayload().hasData(StandardModelData.CanvasModel)) {
            inputContent = this.getPayload().get(StandardModelData.CanvasModel);
            this.getPayload().put(StandardModelData.Request.toString(), inputContent);
        } else {
            inputContent = this.getPayload().get(this.inputModelKey);
        }

        Result<TModel, String> model = this.modelParser.parse(inputContent);
        if (model.isErr()) {
            return this.error(String.format("Failed parsing parsing model (%s)", model.getError()));
        } else {
            Result<TIntermediateModel, String> generatedModel = this.reverseRenderModel(model.getValue());

            if (generatedModel.isErr()) {
                String errorMsg = String.format("Failed to generate intermediate model from model data (%s)", generatedModel.getError());
                this.recordAudit("reverse-render", errorMsg);
                return this.error(errorMsg);
            } else {
                this.recordAudit("reverse-render", generatedModel.getValue().serialize());
                return this.outboundSignal(this.getSuccessSignalId())
                        .withPayloadData(InterpretationData.Model, model.getValue())
                        .withPayloadData(this.outputModelKey, generatedModel.getValue().serialize()).mono();
            }
        }
    }

    protected Result<TIntermediateModel, String> reverseRenderModel(TModel model) {
        this.recordAudit("input-model", model.toString());
        final var modelAssets = getPayload().<TModelAssets>get(StandardModelData.ModelAssets.toString());
        return this.reverseRenderFunction.reverseRenderModelToIR(model, getModel(), modelAssets);
    }

    public String getDescription() {
        return "Reverse Render the given model into its intermediate representation";
    }

}
