package org.rj.modelgen.llm.models.interpretation.states;

import org.rj.modelgen.llm.intrep.core.model.IntermediateModel;
import org.rj.modelgen.llm.models.interpretation.signals.InterpretationModelStandardSignals;
import org.rj.modelgen.llm.statemodel.states.common.SubmitGenerationRequestToLlm;
import org.rj.modelgen.llm.validation.impl.IntermediateModelSanitizer;

public class SubmitInterpretationRequestToLlm extends SubmitGenerationRequestToLlm {
    public SubmitInterpretationRequestToLlm(IntermediateModelSanitizer<? extends IntermediateModel> modelSanitizer) {
        super(SubmitInterpretationRequestToLlm.class, modelSanitizer);
    }

    @Override
    public String getDescription() {
        return "Submit interpretation request to LLM";
    }

    @Override
    public String getSuccessSignalId() {
        return InterpretationModelStandardSignals.SuccessfulLlmExecutionResponse.toString();
    }
}
