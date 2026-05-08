package org.rj.modelgen.llm.models.generation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.rj.modelgen.llm.intrep.assets.ModelAssets;
import org.rj.modelgen.llm.intrep.graph.IntermediateGraphModel;
import org.rj.modelgen.llm.state.ModelInterfaceExecutionResult;
import org.rj.modelgen.llm.state.ModelInterfaceState;

import java.util.List;
import java.util.Optional;

public abstract class GenerationResult<TIntermediateModel extends IntermediateGraphModel<?, ?, ?, ?>, TModelAssets extends ModelAssets> {
    private boolean successful;
    private TIntermediateModel intermediateModel;
    private TModelAssets modelAssets;
    private List<String> validationMessages;
    private ModelInterfaceExecutionResult executionResults;

    public GenerationResult() {

    }

    public GenerationResult(boolean successful, TIntermediateModel intermediateModel, TModelAssets modelAssets,
                            List<String> validationMessages, ModelInterfaceExecutionResult executionResults) {
        this.successful = successful;
        this.intermediateModel = intermediateModel;
        this.modelAssets = modelAssets;
        this.validationMessages = validationMessages;
        this.executionResults = executionResults;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public TIntermediateModel getIntermediateModel() {
        return intermediateModel;
    }

    public TModelAssets getModelAssets() {
        return modelAssets;
    }

    public List<String> getValidationMessages() {
        return validationMessages;
    }

    public ModelInterfaceExecutionResult getExecutionResults() {
        return executionResults;
    }

    @JsonIgnore
    public Optional<String> getLastError() {
        return Optional.ofNullable(executionResults)
                .map(ModelInterfaceExecutionResult::getResult)
                .map(ModelInterfaceState::getLastError);
    }
}
