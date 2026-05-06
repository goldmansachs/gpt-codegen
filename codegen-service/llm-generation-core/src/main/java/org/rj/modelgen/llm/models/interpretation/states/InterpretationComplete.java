package org.rj.modelgen.llm.models.interpretation.states;

import org.json.JSONObject;
import org.rj.modelgen.llm.intrep.core.model.IntermediateModel;
import org.rj.modelgen.llm.models.interpretation.data.InterpretationPayloadData;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.state.ModelInterfaceStateType;
import reactor.core.publisher.Mono;

import java.util.List;

public abstract class InterpretationComplete<TModel extends IntermediateModel> extends ModelInterfaceState{
    private TModel intermediateModel;
    private String interpretationResult;
    private List<String> validationMessages = List.of();

    public InterpretationComplete(Class<? extends InterpretationComplete<TModel>> cls) {
        super(cls, ModelInterfaceStateType.TERMINAL_SUCCESS);
    }

    @Override
    public String getDescription() {
        return "Model interpretation complete";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal modelInterfaceSignal) {
        this.intermediateModel = getPayload().get(InterpretationPayloadData.ReverseRenderedIntermediateModel);
        this.interpretationResult = getPayload().get(InterpretationPayloadData.InterpretationResult);
        this.validationMessages = getPayload().get(InterpretationPayloadData.ValidationMessages);

        return terminalSignal();
    }

    public TModel getIntermediateModel() {
        return intermediateModel;
    }

    public String getInterpretationResult() {
        return interpretationResult;
    }

    public List<String> getValidationMessages() {
        return validationMessages;
    }

    public void setIntermediateModel(TModel intermediateModel) {
        this.intermediateModel = intermediateModel;
    }

    public void setInterpretationResult(String interpretationResult) {
        this.interpretationResult = interpretationResult;
    }

    public void setValidationMessages(List<String> validationMessages) {
        this.validationMessages = validationMessages;
    }

    public String getStringifiedInterpretation() {
        return this.interpretationResult.toString();
    }
}