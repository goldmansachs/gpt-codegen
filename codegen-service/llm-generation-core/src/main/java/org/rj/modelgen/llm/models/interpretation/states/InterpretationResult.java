package org.rj.modelgen.llm.models.interpretation.states;

import org.rj.modelgen.llm.intrep.core.model.IntermediateModel;
import org.rj.modelgen.llm.state.ModelInterfaceExecutionResult;

import java.util.List;
import java.util.Optional;

public class InterpretationResult<TModel extends IntermediateModel> {
    private final boolean successful;
    private final TModel intermediateModel;
    private final String interpretationResult;
    private final List<String> validationMessages;
    private final ModelInterfaceExecutionResult executionResults;

    protected InterpretationResult(boolean successful, TModel intermediateModel, String interpretationResult, List<String> validationMessages, ModelInterfaceExecutionResult executionResults) {
        this.successful = successful;
        this.intermediateModel = intermediateModel;
        this.interpretationResult = interpretationResult;
        this.validationMessages = validationMessages;
        this.executionResults = executionResults;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public TModel getIntermediateModel() {
        return intermediateModel;
    }

    public String getInterpretationResult() {
        return interpretationResult;
    }

    public List<String> validationMessages() {
        return validationMessages;
    }

    public ModelInterfaceExecutionResult getExecutionResults() {
        return executionResults;
    }

    public static <TModel extends IntermediateModel> InterpretationResult<TModel> fromModelExecutionResult(
            ModelInterfaceExecutionResult result, Class<? extends InterpretationComplete<TModel>> completeStateClass) {
        final var successResult = Optional.ofNullable(result).map(ModelInterfaceExecutionResult::getResult)
                .flatMap(state -> state.getAs(completeStateClass));

        return successResult.map(res ->
                new InterpretationResult<>(true, res.getIntermediateModel(), res.getInterpretationResult(), res.getValidationMessages(), result)
        ).orElseGet(() ->
                new InterpretationResult<>(false, null, null, null, result)
        );
    }
}
