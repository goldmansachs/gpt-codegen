package org.rj.modelgen.forms.models.generation.states;

import org.rj.modelgen.forms.models.generation.data.FormGenerationModelInputPayload;
import org.rj.modelgen.forms.models.generation.signals.FormGenerationSignals;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates the correctness of the generated A2UI form output.
 * Performs structural checks on the final A2UI output before completing the pipeline.
 */
public class ValidateFormModelCorrectness extends ModelInterfaceState {
    private static final Logger LOG = LoggerFactory.getLogger(ValidateFormModelCorrectness.class);

    public ValidateFormModelCorrectness() {
        super(ValidateFormModelCorrectness.class);
    }

    @Override
    public String getDescription() {
        return "Validate correctness of generated A2UI form output";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal input) {
        final String a2uiOutput = getPayload().get(FormGenerationModelInputPayload.A2UI_OUTPUT);
        final List<String> validationMessages = new ArrayList<>();

        if (a2uiOutput == null || a2uiOutput.isBlank()) {
            LOG.error("A2UI form output is null or empty after generation");
            validationMessages.add("Generated A2UI form output is null or empty");
        }

        getPayload().put(StandardModelData.ModelValidationMessages, validationMessages);

        return outboundSignal(FormGenerationSignals.OutputValidated)
                .withPayloadData(StandardModelData.ModelValidationMessages, validationMessages)
                .mono();
    }
}
