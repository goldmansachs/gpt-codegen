package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.ElementConnection;
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
import java.util.function.Function;
import java.util.stream.Collectors;

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
        mergeAffectedNodes();

        final boolean isReverseRender = getPayload().hasData(SerializedReverseRender) && !getPayload().hasData(MultiLevelModelStandardPayloadData.DetailLevelModel);

        final Map<String, String> validationMessages = runValidation(isReverseRender);

        return isReverseRender
                ? handleExistingValidations(validationMessages)
                : handleGeneratedValidations(validationMessages);
    }

    private void mergeAffectedNodes() {
        final ImpactAnalysisResult impactAnalysis = getPayload().getOrElse(MultiLevelModelStandardPayloadData.ImpactAnalysis, (ImpactAnalysisResult) null);
        if (impactAnalysis == null) return;

        // Full model: OriginalDetailLevelModel (retry) or SerializedReverseRender (copilot first pass)
        String originalModelContent = getPayload().getOrElse(MultiLevelModelStandardPayloadData.OriginalDetailLevelModel, (String) null);
        if (originalModelContent == null || originalModelContent.isBlank()) {
            originalModelContent = getPayload().getOrElse(SerializedReverseRender, (String) null);
        }

        final String generatedModelContent = getPayload().getOrElse(MultiLevelModelStandardPayloadData.DetailLevelModel, (String) null);

        if (originalModelContent == null || originalModelContent.isBlank() || generatedModelContent == null || generatedModelContent.isBlank()) {
            return; // Nothing to merge (e.g. copilot initial pass before first generation)
        }

        final var parser = new IntermediateModelParser<>(BpmnIntermediateModel.class);
        final var originalModel = parser.parse(originalModelContent).orElse(null);
        final var generatedModel = parser.parse(generatedModelContent).orElse(null);

        if (originalModel == null || generatedModel == null) {
            LOG.warn("Failed to parse original or generated model, skipping merge.");
            clearScopingData();
            return;
        }

        // Build lookup of generated (affected) nodes by ID
        Map<String, ElementNode> generatedNodesById = generatedModel.getNodes().stream()
                .collect(Collectors.toMap(ElementNode::getId, Function.identity(), (a, b) -> b));
        
        // Save the scoped (subset) model before merging for prompt reference on retry
        getPayload().put(MultiLevelModelStandardPayloadData.ScopedDetailLevelModel, generatedModel.serialize());

        Set<String> removeIds = new HashSet<>(impactAnalysis.getRemoveNodeIds());

        // Merge: use generated version for affected nodes, original for unaffected, skip removed
        List<ElementNode> mergedNodes = new ArrayList<>();
        Set<String> processedIds = new HashSet<>();

        for (ElementNode originalNode : originalModel.getNodes()) {
            String id = originalNode.getId();
            if (removeIds.contains(id)) continue;
            mergedNodes.add(generatedNodesById.getOrDefault(id, originalNode));
            processedIds.add(id);
        }

        // Add any new nodes from the generated model
        for (ElementNode generatedNode : generatedModel.getNodes()) {
            if (!processedIds.contains(generatedNode.getId())) {
                mergedNodes.add(generatedNode);
            }
        }

        // Sanitize dangling connections to removed nodes
        if (!removeIds.isEmpty()) {
            int danglingRemoved = sanitizeConnections(mergedNodes, removeIds);
            if (danglingRemoved > 0) {
                LOG.warn("Removed {} dangling connection(s) referencing removed node IDs: {}", danglingRemoved, removeIds);
            }
        }

        int restoredCount = (int) mergedNodes.stream()
                .filter(node -> !generatedNodesById.containsKey(node.getId()))
                .count();

        generatedModel.setNodes(mergedNodes);

        LOG.info("Merge complete: {} unaffected nodes restored, {} affected from generation, {} removed. Final: {} nodes.",
                restoredCount, generatedNodesById.size(), removeIds.size(), mergedNodes.size());

        // Store merged model and clear scoping data for a fresh cycle
        getPayload().put(MultiLevelModelStandardPayloadData.DetailLevelModel, generatedModel.serialize());
        clearScopingData();
    }

    private void clearScopingData() {
        getPayload().remove(MultiLevelModelStandardPayloadData.OriginalDetailLevelModel);
        getPayload().remove(MultiLevelModelStandardPayloadData.ImpactAnalysis);
        getPayload().remove(MultiLevelModelStandardPayloadData.ImpactAnalysisMaskingInstructions);
    }

    private int sanitizeConnections(List<ElementNode> nodes, Set<String> removedIds) {
        int count = 0;
        for (ElementNode node : nodes) {
            Collection<ElementConnection> connections = node.getConnectedTo();
            if (connections == null || connections.isEmpty()) continue;
            int before = connections.size();
            connections.removeIf(conn -> removedIds.contains(conn.getTargetNode()));
            count += (before - connections.size());
        }
        return count;
    }

    private Map<String, String> runValidation(boolean isReverseRender) {
        final String content = isReverseRender ? getPayload().get(SerializedReverseRender).toString() : getPayload().get(MultiLevelModelStandardPayloadData.DetailLevelModel);

        if(content.isEmpty()) {
            throw new LlmGenerationModelException("No BPMN content to validate");
        }

        final var parser = new IntermediateModelParser<>(BpmnIntermediateModel.class);
        final var model = parser.parse(content).orElseThrow(e -> new LlmGenerationModelException(String.format(
                "Validate BPMN Detail Level Intermediate Model Response could not parse detail-level intermediate model: %s (content: %s)", e, content)));

        final Set<PayloadVariable> startingPayload = getPayload().get(MultiLevelModelStandardPayloadData.ProcessVariables);
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

        getPayload().put(MultiLevelModelStandardPayloadData.OriginalDetailLevelModel, detailModel);

        final var impact = new ImpactAnalysisResult(
                new ArrayList<>(errorNodeIds), false, List.of(),
                "Validation errors found in nodes: " + errorNodeIds);
        getPayload().put(MultiLevelModelStandardPayloadData.ImpactAnalysis, impact);

        final var parser = new IntermediateModelParser<>(BpmnIntermediateModel.class);
        final var model = parser.parse(detailModel).orElse(null);
        if (model == null) return;

        List<ElementNode> errorNodes = model.getNodes().stream()
                .filter(node -> errorNodeIds.contains(node.getId()))
                .collect(Collectors.toList());
        BpmnIntermediateModel scopedModel = new BpmnIntermediateModel();
        scopedModel.setNodes(errorNodes);
        scopedModel.setCommentary(model.getCommentary());
        getPayload().put(MultiLevelModelStandardPayloadData.ScopedDetailLevelModel, scopedModel.serialize());

        getPayload().put(MultiLevelModelStandardPayloadData.ImpactAnalysisMaskingInstructions, "Nodes with errors: " + errorNodeIds);

        LOG.info("Error masking set up for retry: {} error nodes identified out of {} total nodes", errorNodeIds.size(), model.getNodes().size());
    }

    private BpmnComponentLibrary getComponentLibrary() {
        return Optional.ofNullable(getModel())
                .map(m -> m.getAs(BpmnMultiLevelGenerationModel.class))
                .map(BpmnMultiLevelGenerationModel::getComponentLibrary)
                .orElse(null);
    }
}
