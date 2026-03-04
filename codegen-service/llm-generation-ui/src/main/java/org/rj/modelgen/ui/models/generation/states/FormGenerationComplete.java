package org.rj.modelgen.forms.models.generation.states;

import org.rj.modelgen.forms.models.generation.data.FormGenerationModelInputPayload;
import org.rj.modelgen.llm.state.GenerationComplete;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Terminal state for the form generation pipeline.
 * Captures the final outputs from the pipeline stages: the formalised intent
 * and the generated A2UI form output.
 */
public class FormGenerationComplete extends GenerationComplete {
    private String formalisedIntent;
    private String a2uiOutput;
    private List<String> formValidationMessages = List.of();

    public FormGenerationComplete() {
        super(FormGenerationComplete.class);
    }

    @Override
    public String getDescription() {
        return "Form generation pipeline complete";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal inputSignal) {
        this.formalisedIntent = getPayload().get(FormGenerationModelInputPayload.FORMALISED_INTENT);
        this.a2uiOutput = getPayload().get(FormGenerationModelInputPayload.A2UI_OUTPUT);
        this.formValidationMessages = getPayload().get(StandardModelData.ModelValidationMessages);

        return terminalSignal();
    }

    @Override
    public String getStringifiedModel() {
        return a2uiOutput;
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
}
