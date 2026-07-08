package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.models.generation.base.signals.BpmnGenerationSignals;
import org.rj.modelgen.bpmn.models.generation.multilevel.BpmnMultiLevelGenerationModel;
import org.rj.modelgen.bpmn.models.generation.multilevel.data.ImpactAnalysisResult;
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

import static org.rj.modelgen.bpmn.generation.BpmnConstants.Validation.FULL_PROCESS;
import static org.rj.modelgen.bpmn.models.generation.base.context.BpmnPromptPlaceholders.DETAIL_MODEL_VALIDATION_ISSUES;
import static org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData.InitialValidations;
import static org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData.SerializedReverseRender;

public class ValidateBpmnLlmDetailLevelIntermediateModelResponse extends ModelInterfaceState implements CommonStateInterface {

    private static final Logger LOG = LoggerFactory.getLogger(ValidateBpmnLlmDetailLevelIntermediateModelResponse.class);
    private final ValidateBpmnModel bpmnModelValidator;

    public ValidateBpmnLlmDetailLevelIntermediateModelResponse(BpmnGlobalVariableLibrary globalVariableLibrary, ValidateBpmnModel bpmnModelValidator) {
        super(ValidateBpmnLlmDetailLevelIntermediateModelResponse.class);
        this.bpmnModelValidator = bpmnModelValidator;
    }

    @Override
    public String getDescription() {
        return "Check output of the detail-level phase and determine whether to iterate or proceed to model generation";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal modelInterfaceSignal) {

        final boolean isReverseRender = getPayload().hasData(SerializedReverseRender) && !getPayload().hasData(MultiLevelModelStandardPayloadData.DetailLevelModel);

        final Map<String, String> validationMessages = runValidation(isReverseRender);

        return isReverseRender
                ? handleExistingValidations(validationMessages)
                : handleGeneratedValidations(validationMessages);
    }

    private Map<String, String> runValidation(boolean isReverseRender) {
        final String content = isReverseRender ? getPayload().get(SerializedReverseRender).toString() : getPayload().get(MultiLevelModelStandardPayloadData.DetailLevelModel);

        if(content.isEmpty()) {
            throw new LlmGenerationModelException("No BPMN content to validate");
        }

        final var parser = new IntermediateModelParser<>(BpmnIntermediateModel.class);
        final var model = parser.parse(content).orElseThrow(e -> new LlmGenerationModelException(String.format(
                "Validate BPMN Detail Level Intermediate Model Response could not parse detail-level intermediate model: %s (content: %s)", e, content)));

        final Collection<PayloadVariable> processVarsRaw = getPayload().get(MultiLevelModelStandardPayloadData.ProcessVariables);
        final Set<PayloadVariable> startingPayload = processVarsRaw != null ? new HashSet<>(processVarsRaw) : new HashSet<>();
        final List<IntermediateModelValidationError> validations = bpmnModelValidator.validate(model, startingPayload);

        final Map<String, String> validationMessages = new LinkedHashMap<>();
        validations.stream().collect(Collectors.groupingBy(IntermediateModelValidationError::getLocation))
                .forEach((nodeName, nodeValidations) -> {
                    final var issuesList = new StringBuilder();
                    for (int i = 0; i < nodeValidations.size(); i++) {
                        issuesList.append(String.format("\n  %d. %s", i + 1, nodeValidations.get(i).getError()));
                    }
                    String message = String.format("Node Name: %s\n Issues to resolve:%s\n", nodeName, issuesList);
                    LOG.warn(message);
                    validationMessages.put(nodeName, message);
                });

        return validationMessages;
    }

    private Mono<ModelInterfaceSignal> handleExistingValidations(Map<String, String> validationsByNode) {
        if (validationsByNode.isEmpty()) {
            LOG.info("BPMN intermediate model has no validation issues");
            return outboundSignal(getSuccessSignalId())
                    .withPayloadData(InitialValidations, List.of())
                    .mono();
        }

        return outboundSignal(getSuccessSignalId())
                .withPayloadData(InitialValidations, new ArrayList<>(validationsByNode.values()))
                .mono();
    }

    private Mono<ModelInterfaceSignal> handleGeneratedValidations(Map<String, String> validationsByNode) {
        if (validationsByNode.isEmpty()) {
            LOG.info("BPMN detail-level intermediate model is valid, proceeding to next phase");
            return outboundSignal(getSuccessSignalId()).mono();
        }

        final List<String> initialBpmnValidations = Optional
                .<List<String>>ofNullable(getPayload().get(InitialValidations))
                .orElse(List.of());

        final Map<String, String> newValidationsByNode;

        if (initialBpmnValidations.isEmpty()) {
            newValidationsByNode = validationsByNode;
        } else {
            final Set<String> initialSet = new HashSet<>(initialBpmnValidations);
            newValidationsByNode = validationsByNode.entrySet().stream()
                    .filter(e -> !initialSet.contains(e.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        }

        if (newValidationsByNode.isEmpty()) {
            LOG.info("All {} BPMN validation issue(s) already existed in the initial model", initialBpmnValidations.size());
            return outboundSignal(getSuccessSignalId()).mono();
        }

        final List<String> detailLevelValidations = new ArrayList<>(newValidationsByNode.values());
        LOG.info("Found {} new BPMN validation issue(s). Returning to detail-level phase", detailLevelValidations.size());

        setupErrorNodeMasking(newValidationsByNode.keySet());

        return outboundSignal(BpmnGenerationSignals.IntermediateModelIsInvalid)
                .withPayloadData(DETAIL_MODEL_VALIDATION_ISSUES.getValue(), detailLevelValidations)
                .mono();
    }

    private void setupErrorNodeMasking(Set<String> errorNodeIds) {
        final String detailModel = getPayload().get(MultiLevelModelStandardPayloadData.DetailLevelModel);
        if (detailModel == null || detailModel.isBlank()) return;
        if (errorNodeIds.isEmpty()) return;

        final var parser = new IntermediateModelParser<>(BpmnIntermediateModel.class);
        final var model = parser.parse(detailModel).orElse(null);
        if (model == null) return;

        if (errorNodeIds.contains(FULL_PROCESS)) {
            LOG.info("Process-level error IDs detected in {}; skipping scoped masking to include full model in retry", errorNodeIds);
            clearScopingData();
            return;
        }

        getPayload().put(MultiLevelModelStandardPayloadData.OriginalDetailLevelModel, detailModel);

        final var impact = new ImpactAnalysisResult(
                new ArrayList<>(errorNodeIds), false, List.of(),
                "Validation errors found in nodes: " + errorNodeIds);
        getPayload().put(MultiLevelModelStandardPayloadData.ImpactAnalysis, impact);

        List<ElementNode> errorNodes = model.getAllNodesRecursive()
                .filter(node -> errorNodeIds.contains(node.getId()))
                .collect(Collectors.toList());

        BpmnIntermediateModel scopedModel = new BpmnIntermediateModel();
        scopedModel.setNodes(errorNodes);
        getPayload().put(MultiLevelModelStandardPayloadData.ScopedDetailLevelModel, scopedModel.serialize());
        getPayload().put(MultiLevelModelStandardPayloadData.ImpactAnalysisMaskingInstructions, "Nodes with errors: " + errorNodeIds);

        LOG.info("Error masking set up for retry: {} error nodes identified out of {} total nodes", errorNodeIds.size(), model.getNodes().size());
    }

    private void clearScopingData() {
        getPayload().remove(MultiLevelModelStandardPayloadData.OriginalDetailLevelModel);
        getPayload().remove(MultiLevelModelStandardPayloadData.ImpactAnalysis);
        getPayload().remove(MultiLevelModelStandardPayloadData.ImpactAnalysisMaskingInstructions);
        getPayload().remove(MultiLevelModelStandardPayloadData.ScopedDetailLevelModel);
    }

    private BpmnComponentLibrary getComponentLibrary() {
        return Optional.ofNullable(getModel())
                .map(m -> m.getAs(BpmnMultiLevelGenerationModel.class))
                .map(BpmnMultiLevelGenerationModel::getComponentLibrary)
                .orElse(null);
    }
}
