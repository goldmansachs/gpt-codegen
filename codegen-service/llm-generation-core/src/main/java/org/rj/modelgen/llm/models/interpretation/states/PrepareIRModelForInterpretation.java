package org.rj.modelgen.llm.models.interpretation.states;

import org.json.JSONObject;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import reactor.core.publisher.Mono;

public class PrepareIRModelForInterpretation extends ModelInterfaceState {
    public PrepareIRModelForInterpretation() {
        this(PrepareIRModelForInterpretation.class);
    }

    public PrepareIRModelForInterpretation(Class<? extends ModelInterfaceState> cls) {
        super(cls);
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal inputSignal) {
        return outboundSignal(StandardSignals.SUCCESS, "Successfully Prepared IR Model for Interpretation")
                .withPayloadData(StandardModelData.IntermediateModel, this.getPayload().hasData(StandardModelData.CanvasModel) ?this.getPayload().get(StandardModelData.CanvasModel).toString() : new JSONObject(this.getPayload().get(StandardModelData.Request).toString()))
                .mono();
    }

    @Override
    public String getDescription() {
        return "Prepare IR model for interpretation";
    }
}
