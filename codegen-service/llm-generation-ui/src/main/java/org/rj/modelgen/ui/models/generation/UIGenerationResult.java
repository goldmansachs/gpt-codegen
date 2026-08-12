package org.rj.modelgen.ui.models.generation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.rj.modelgen.ui.models.generation.states.UIGenerationComplete;
import org.rj.modelgen.llm.models.generation.GenerationResult;
import org.rj.modelgen.llm.state.ModelInterfaceExecutionResult;
import org.rj.modelgen.llm.state.ModelInterfaceState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Result of a UI generation pipeline execution.
 * Contains the generated UI output in whatever target format was used.
 */
public class UIGenerationResult extends GenerationResult {
    private final boolean successful;
    private final String uiOutput;
    private final List<String> uiValidationMessages;
    private final ModelInterfaceExecutionResult executionResults;

    public static UIGenerationResult fromModelExecutionResult(ModelInterfaceExecutionResult result) {
        final var successResult = Optional.ofNullable(result).map(ModelInterfaceExecutionResult::getResult)
                .flatMap(state -> state.getAs(UIGenerationComplete.class));

        return successResult.map(res ->
            new UIGenerationResult(true, res.getUIOutput(), res.getUIValidationMessages(), result)
        ).orElseGet(() ->
            new UIGenerationResult(false, null, null, result)
        );
    }

    protected UIGenerationResult(boolean successful, String uiOutput,
                                 List<String> uiValidationMessages, ModelInterfaceExecutionResult executionResults) {
        this.successful = successful;
        this.uiOutput = uiOutput;
        this.uiValidationMessages = Optional.ofNullable(uiValidationMessages).orElseGet(ArrayList::new);
        this.executionResults = executionResults;
    }

    public boolean isSuccessful() {
        return successful;
    }


    public String getUIOutput() {
        return uiOutput;
    }

    public List<String> getUIValidationMessages() {
        return uiValidationMessages;
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
