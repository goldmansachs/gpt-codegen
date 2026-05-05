package org.rj.modelgen.bpmn.interpretation.data;

import org.json.JSONObject;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.llm.models.interpretation.data.InterpretationPayloadData;
import org.rj.modelgen.llm.models.interpretation.states.InterpretationComplete;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import reactor.core.publisher.Mono;

public class BpmnInterpretationComplete extends InterpretationComplete<BpmnIntermediateModel> {

    public BpmnInterpretationComplete() {
        super(BpmnInterpretationComplete.class);
    }

    @Override
    public String getDescription() {
        return "BPMN interpretation complete";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal modelInterfaceSignal) {
        String rawModel = this.getPayload().get(InterpretationPayloadData.ReverseRenderedIntermediateModel);
        setIntermediateModel(BpmnIntermediateModel.fromJson(new JSONObject(rawModel)));
        setInterpretationResult(getPayload().get(InterpretationPayloadData.InterpretationResult));
        setValidationMessages(getPayload().get(InterpretationPayloadData.ValidationMessages));

        return terminalSignal();
    }
}
