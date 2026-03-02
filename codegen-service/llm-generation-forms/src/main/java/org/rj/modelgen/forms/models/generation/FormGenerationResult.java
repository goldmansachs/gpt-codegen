package org.rj.modelgen.forms.models.generation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.rj.modelgen.forms.models.generation.states.FormGenerationComplete;
import org.rj.modelgen.llm.models.generation.GenerationResult;
import org.rj.modelgen.llm.state.ModelInterfaceExecutionResult;
import org.rj.modelgen.llm.state.ModelInterfaceState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Result of the form generation pipeline execution.
 * Contains the formalised intent (structured description of form fields)
 * and the generated A2UI form output.
 */
public class FormGenerationResult extends GenerationResult {
    private final boolean successful;
    private final String formalisedIntent;
    private final String a2uiOutput;
    private final List<String> formValidationMessages;
    private final ModelInterfaceExecutionResult executionResults;

    public static FormGenerationResult fromModelExecutionResult(ModelInterfaceExecutionResult result) {
        final var successResult = Optional.ofNullable(result).map(ModelInterfaceExecutionResult::getResult)
                .flatMap(state -> state.getAs(FormGenerationComplete.class));

        return successResult.map(res ->
            new FormGenerationResult(true, res.getFormalisedIntent(), res.getA2UIOutput(), res.getFormValidationMessages(), result)
        ).orElseGet(() ->
            new FormGenerationResult(false, null, null, null, result)
        );
    }

    private FormGenerationResult(boolean successful, String formalisedIntent, String a2uiOutput,
                                 List<String> formValidationMessages, ModelInterfaceExecutionResult executionResults) {
        this.successful = successful;
        this.formalisedIntent = formalisedIntent;
        this.a2uiOutput = a2uiOutput;
        this.formValidationMessages = Optional.ofNullable(formValidationMessages).orElseGet(ArrayList::new);
        this.executionResults = executionResults;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getFormalisedIntent() {
        return formalisedIntent;
    }

    public String getA2UIOutput() {
        return a2uiOutput;
    }

    public List<String> getFormValidationMessages() {
        return formValidationMessages;
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
