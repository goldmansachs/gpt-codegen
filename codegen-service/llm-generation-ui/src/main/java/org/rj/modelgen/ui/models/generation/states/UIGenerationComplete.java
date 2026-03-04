package org.rj.modelgen.ui.models.generation.states;

import org.rj.modelgen.ui.models.generation.data.UIGenerationModelInputPayload;
import org.rj.modelgen.llm.state.GenerationComplete;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Terminal state for the UI generation pipeline.
 * Captures the final outputs from the pipeline stages: the formalised intent
 * and the generated UI output.
 */
public class UIGenerationComplete extends GenerationComplete {
    private String formalisedIntent;
    private String uiOutput;
    private List<String> uiValidationMessages = List.of();

    public UIGenerationComplete() {
        super(UIGenerationComplete.class);
    }

    @Override
    public String getDescription() {
        return "UI generation pipeline complete";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal inputSignal) {
        this.formalisedIntent = getPayload().get(UIGenerationModelInputPayload.FORMALISED_INTENT);
        this.uiOutput = getPayload().get(UIGenerationModelInputPayload.UI_OUTPUT);
        this.uiValidationMessages = getPayload().get(StandardModelData.ModelValidationMessages);

        return terminalSignal();
    }

    @Override
    public String getStringifiedModel() {
        return uiOutput;
    }

    public String getFormalisedIntent() {
        return formalisedIntent;
    }

    public String getUIOutput() {
        return uiOutput;
    }

    public List<String> getUIValidationMessages() {
        return uiValidationMessages;
    }
}
