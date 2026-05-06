package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.models.generation.base.signals.BpmnGenerationSignals;
import org.rj.modelgen.bpmn.models.generation.multilevel.BpmnMultiLevelGenerationModel;
import org.rj.modelgen.bpmn.models.generation.validation.PayloadVariable;
import org.rj.modelgen.bpmn.models.generation.validation.ValidateBpmnModel;
import org.rj.modelgen.llm.exception.LlmGenerationModelException;
import org.rj.modelgen.llm.intrep.IntermediateModelParser;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.statemodel.signals.common.CommonStateInterface;
import org.rj.modelgen.llm.validation.beans.IntermediateModelValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

import static org.rj.modelgen.bpmn.models.generation.base.context.BpmnPromptPlaceholders.DETAIL_MODEL_VALIDATION_ISSUES;
import static org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData.InitialValidations;
import static org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData.SerializedReverseRender;

public class ValidateBpmnLlmDetailLevelIntermediateModelResponse extends ModelInterfaceState implements CommonStateInterface {

    private static final Logger LOG = LoggerFactory.getLogger(ValidateBpmnLlmDetailLevelIntermediateModelResponse.class);
    private final BpmnGlobalVariableLibrary globalVariableLibrary;
    private final ValidateBpmnModel bpmnModelValidator;

    public ValidateBpmnLlmDetailLevelIntermediateModelResponse(BpmnGlobalVariableLibrary globalVariableLibrary, ValidateBpmnModel bpmnModelValidator) {
        super(ValidateBpmnLlmDetailLevelIntermediateModelResponse.class);
        this.globalVariableLibrary = globalVariableLibrary;
        this.bpmnModelValidator = bpmnModelValidator;
    }

    @Override
    public String getDescription() {
        return "Check output of the detail-level phase and determine whether to iterate or proceed to model generation";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal modelInterfaceSignal) {
        final boolean isReverseRender = getPayload().hasData(SerializedReverseRender) && !getPayload().hasData(MultiLevelModelStandardPayloadData.DetailLevelModel);

        final List<String> validationMessages = runValidation(isReverseRender);

        return isReverseRender
                ? handleExistingValidations(validationMessages)
                : handleGeneratedValidations(validationMessages);
    }

    private List<String> runValidation(boolean isReverseRender) {
        final String content = isReverseRender ? getPayload().get(SerializedReverseRender).toString() : getPayload().get(MultiLevelModelStandardPayloadData.DetailLevelModel);

        if(content.isEmpty()) {
            throw new LlmGenerationModelException("No BPMN content to validate");
        }

        final var parser = new IntermediateModelParser<>(BpmnIntermediateModel.class);
        final var model = parser.parse(content).orElseThrow(e -> new LlmGenerationModelException(String.format(
                "Validate BPMN Detail Level Intermediate Model Response could not parse detail-level intermediate model: %s (content: %s)", e, content)));

        final Set<PayloadVariable> startingPayload = getPayload().get(MultiLevelModelStandardPayloadData.ProcessVariables);
        final List<IntermediateModelValidationError> validations = bpmnModelValidator.validate(model, startingPayload);

        final List<String> validationMessages = new ArrayList<>();
        validations.stream().collect(Collectors.groupingBy(IntermediateModelValidationError::getLocation))
                .forEach((nodeName, nodeValidations) -> {
                    String message = String.format("Node Name: %s, Issues to resolve: %s", nodeName,
                            nodeValidations.stream().map(IntermediateModelValidationError::getError).collect(Collectors.joining(", ")));
                    LOG.warn(message);
                    validationMessages.add(message);
                });

        return validationMessages;
    }

    private Mono<ModelInterfaceSignal> handleExistingValidations(List<String> validationMessages) {
        if (validationMessages.isEmpty()) {
            LOG.info("BPMN intermediate model has no validation issues");
            return outboundSignal(getSuccessSignalId())
                    .withPayloadData(InitialValidations, List.of())
                    .mono();
        }

        return outboundSignal(getSuccessSignalId())
                .withPayloadData(InitialValidations, validationMessages)
                .mono();
    }

    private Mono<ModelInterfaceSignal> handleGeneratedValidations(List<String> validationMessages) {
        if (validationMessages.isEmpty()) {
            LOG.info("BPMN detail-level intermediate model is valid, proceeding to next phase");
            return outboundSignal(getSuccessSignalId()).mono();
        }

        final List<String> initialBpmnValidations = Optional
                .<List<String>>ofNullable(getPayload().get(InitialValidations))
                .orElse(List.of());

        final List<String> detailLevelValidations;

        if (initialBpmnValidations.isEmpty()) {
            detailLevelValidations = validationMessages;
        } else {
            final Set<String> initialSet = new HashSet<>(initialBpmnValidations);
            detailLevelValidations = validationMessages.stream()
                    .filter(msg -> !initialSet.contains(msg))
                    .collect(Collectors.toList());
        }

        if (detailLevelValidations.isEmpty()) {
            LOG.info("All {} BPMN validation issue(s) already existed in the initial model", validationMessages.size());
            return outboundSignal(getSuccessSignalId()).mono();
        }

        LOG.info("Found {} new BPMN validation issue(s). Returning to detail-level phase", detailLevelValidations.size());
        return outboundSignal(BpmnGenerationSignals.IntermediateModelIsInvalid)
                .withPayloadData(DETAIL_MODEL_VALIDATION_ISSUES.getValue(), detailLevelValidations)
                .mono();
    }

    private BpmnComponentLibrary getComponentLibrary() {
        return Optional.ofNullable(getModel())
                .map(m -> m.getAs(BpmnMultiLevelGenerationModel.class))
                .map(BpmnMultiLevelGenerationModel::getComponentLibrary)
                .orElse(null);
    }
}
