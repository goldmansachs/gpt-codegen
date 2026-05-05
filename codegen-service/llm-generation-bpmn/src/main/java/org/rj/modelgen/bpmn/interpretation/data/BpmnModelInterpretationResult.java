package org.rj.modelgen.bpmn.interpretation.data;

import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.llm.models.interpretation.states.InterpretationResult;
import org.rj.modelgen.llm.state.ModelInterfaceExecutionResult;

public class BpmnModelInterpretationResult extends InterpretationResult<BpmnIntermediateModel> {

    private BpmnModelInterpretationResult(boolean successful, BpmnIntermediateModel intermediateModel, String interpretationResult, java.util.List<String> validationMessages, ModelInterfaceExecutionResult executionResults) {
        super(successful, intermediateModel, interpretationResult, validationMessages, executionResults);
    }

    public static BpmnModelInterpretationResult fromModelExecutionResult(ModelInterfaceExecutionResult result) {
        InterpretationResult<BpmnIntermediateModel> base = InterpretationResult.fromModelExecutionResult(result, BpmnInterpretationComplete.class);
        return new BpmnModelInterpretationResult(base.isSuccessful(), base.getIntermediateModel(), base.getInterpretationResult(), base.validationMessages(), base.getExecutionResults());
    }
}