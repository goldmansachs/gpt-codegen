package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.ElementConnection;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.models.generation.multilevel.data.ImpactAnalysisResult;
import org.rj.modelgen.llm.intrep.IntermediateModelParser;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.statemodel.signals.common.CommonStateInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData.SerializedReverseRender;

public class MergeScopedBpmnDetailLevelModel extends ModelInterfaceState implements CommonStateInterface {

    private static final Logger LOG = LoggerFactory.getLogger(MergeScopedBpmnDetailLevelModel.class);

    public MergeScopedBpmnDetailLevelModel() {
        super(MergeScopedBpmnDetailLevelModel.class);
    }

    @Override
    public String getDescription() {
        return "Merge scoped detail-level model fragments back into the full model";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal modelInterfaceSignal) {
        mergeAffectedNodes();
        return outboundSignal(getSuccessSignalId()).mono();
    }

    private void mergeAffectedNodes() {
        final ImpactAnalysisResult impactAnalysis = getPayload().getOrElse(MultiLevelModelStandardPayloadData.ImpactAnalysis, (ImpactAnalysisResult) null);
        if (impactAnalysis == null) {
            LOG.debug("No impact analysis available - skipping merge");
            return;
        }

        // Full model: OriginalDetailLevelModel (retry) or SerializedReverseRender (copilot first pass)
        String originalModelContent = getPayload().getOrElse(MultiLevelModelStandardPayloadData.OriginalDetailLevelModel, (String) null);
        if (originalModelContent == null || originalModelContent.isBlank()) {
            originalModelContent = getPayload().getOrElse(SerializedReverseRender, (String) null);
        }

        final String generatedModelContent = getPayload().getOrElse(MultiLevelModelStandardPayloadData.DetailLevelModel, (String) null);

        if (originalModelContent == null || originalModelContent.isBlank() || generatedModelContent == null || generatedModelContent.isBlank()) {
            LOG.warn("Merge skipped - original model is {} and generated model is {}",
                    (originalModelContent == null || originalModelContent.isBlank()) ? "missing" : "present",
                    (generatedModelContent == null || generatedModelContent.isBlank()) ? "missing" : "present");
            return;
        }

        final var parser = new IntermediateModelParser<>(BpmnIntermediateModel.class);
        final var originalModel = parser.parse(originalModelContent).orElse(null);
        final var generatedModel = parser.parse(generatedModelContent).orElse(null);

        if (originalModel == null || generatedModel == null) {
            LOG.warn("Failed to parse original or generated model, skipping merge.");
            clearScopingData();
            return;
        }

        // Build lookup from ALL generated nodes (main process + subModels)
        Map<String, ElementNode> generatedNodesById = generatedModel.getAllNodesRecursive()
                .collect(Collectors.toMap(ElementNode::getId, Function.identity(), (a, b) -> b));

        // Index generated subModel nodes by subProcessId so new nodes can be routed to the correct subprocess
        Map<String, List<ElementNode>> generatedSubModelNodes = new HashMap<>();
        if (generatedModel.hasSubModels()) {
            for (BpmnIntermediateModel genSub : generatedModel.getSubModels()) {
                String subId = genSub.getSubProcessConfig() != null
                        ? genSub.getSubProcessConfig().getSubProcessId() : null;
                if (subId != null) {
                    generatedSubModelNodes.put(subId, new ArrayList<>(genSub.getNodes()));
                }
            }
        }

        String originalCommentary = generatedModel.getCommentary();
        generatedModel.setCommentary(null);
        getPayload().put(MultiLevelModelStandardPayloadData.ScopedDetailLevelModel, generatedModel.serialize());
        generatedModel.setCommentary(originalCommentary);

        Set<String> removeIds = new HashSet<>(impactAnalysis.getRemoveNodeIds());
        for (String affectedId : impactAnalysis.getAffectedNodeIds()) {
            if (!generatedNodesById.containsKey(affectedId)) {
                removeIds.add(affectedId);
                LOG.info("Affected node '{}' not returned by LLM during scoped retry — treating as implicitly removed/replaced", affectedId);
            }
        }

        // Merge main process nodes
        List<ElementNode> mergedNodes = mergeNodeLists(originalModel.getNodes(), generatedNodesById, removeIds, generatedSubModelNodes);
        int restoredCount = (int) mergedNodes.stream()
                .filter(node -> !generatedNodesById.containsKey(node.getId()))
                .count();
        generatedModel.setNodes(mergedNodes);

        // Merge subModels
        Set<String> processedIds = mergedNodes.stream().map(ElementNode::getId).collect(Collectors.toCollection(HashSet::new));
        List<BpmnIntermediateModel> mergedSubModels = new ArrayList<>();
        Set<String> processedSubIds = new HashSet<>();

        if (originalModel.hasSubModels()) {
            for (BpmnIntermediateModel subModel : originalModel.getSubModels()) {
                String subId = subModel.getSubProcessConfig() != null
                        ? subModel.getSubProcessConfig().getSubProcessId() : null;
                if (subId != null) processedSubIds.add(subId);

                BpmnIntermediateModel mergedSub = new BpmnIntermediateModel();
                mergedSub.setSubProcessConfig(subModel.getSubProcessConfig());
                mergedSub.setCommentary(subModel.getCommentary());

                List<ElementNode> mergedSubNodes = new ArrayList<>();
                for (ElementNode subNode : subModel.getNodes()) {
                    String id = subNode.getId();
                    if (removeIds.contains(id)) continue;
                    mergedSubNodes.add(generatedNodesById.getOrDefault(id, subNode));
                    processedIds.add(id);
                }

                if (subId != null && generatedSubModelNodes.containsKey(subId)) {
                    for (ElementNode genNode : generatedSubModelNodes.get(subId)) {
                        if (!processedIds.contains(genNode.getId()) && !removeIds.contains(genNode.getId())) {
                            mergedSubNodes.add(genNode);
                            processedIds.add(genNode.getId());
                            LOG.info("Added new node '{}' ({}) to subprocess '{}'", genNode.getName(), genNode.getId(), subId);
                        }
                    }
                }

                if (!removeIds.isEmpty()) {
                    sanitizeConnections(mergedSubNodes, removeIds);
                }

                mergedSub.setNodes(mergedSubNodes);
                mergedSubModels.add(mergedSub);
            }
        }

        if (generatedModel.hasSubModels()) {
            for (BpmnIntermediateModel genSub : generatedModel.getSubModels()) {
                String subId = genSub.getSubProcessConfig() != null
                        ? genSub.getSubProcessConfig().getSubProcessId() : null;
                if (subId != null && !processedSubIds.contains(subId)) {
                    mergedSubModels.add(genSub);
                    LOG.info("Added new subprocess '{}' from LLM generation", subId);
                }
            }
        }

        if (!mergedSubModels.isEmpty()) {
            generatedModel.setSubModels(mergedSubModels);
        }

        if (originalModel.getCommentary() != null && !originalModel.getCommentary().isBlank()) {
            generatedModel.setCommentary(originalModel.getCommentary());
        }

        LOG.info("Merge complete: {} unaffected nodes restored, {} affected from generation, {} removed. Final: {} nodes.",
                restoredCount, generatedNodesById.size(), removeIds.size(), mergedNodes.size());

        // Store merged model and clear scoping data for a fresh cycle
        final String mergedModelContent = generatedModel.serialize();
        getPayload().put(MultiLevelModelStandardPayloadData.DetailLevelModel, mergedModelContent);
        recordAudit("full-dl", mergedModelContent);
        clearScopingData();
    }

    private List<ElementNode> mergeNodeLists(List<ElementNode> originalNodes, Map<String, ElementNode> generatedNodesById, Set<String> removeIds, Map<String, List<ElementNode>> generatedSubModelNodes) {
        List<ElementNode> mergedNodes = new ArrayList<>();
        Set<String> processedIds = new HashSet<>();

        for (ElementNode originalNode : originalNodes) {
            String id = originalNode.getId();
            if (removeIds.contains(id)) continue;
            mergedNodes.add(generatedNodesById.getOrDefault(id, originalNode));
            processedIds.add(id);
        }

        for (ElementNode generatedNode : generatedNodesById.values()) {
            if (!processedIds.contains(generatedNode.getId())
                    && !removeIds.contains(generatedNode.getId())
                    && !isInAnyGeneratedSubModel(generatedNode.getId(), generatedSubModelNodes)) {
                mergedNodes.add(generatedNode);
                processedIds.add(generatedNode.getId());
                LOG.info("Added new main-process node '{}' ({})", generatedNode.getName(), generatedNode.getId());
            }
        }

        if (!removeIds.isEmpty()) {
            int danglingRemoved = sanitizeConnections(mergedNodes, removeIds);
            if (danglingRemoved > 0) {
                LOG.warn("Removed {} dangling connection(s) referencing removed node IDs: {}", danglingRemoved, removeIds);
            }
        }

        return mergedNodes;
    }

    private boolean isInAnyGeneratedSubModel(String nodeId, Map<String, List<ElementNode>> generatedSubModelNodes) {
        return generatedSubModelNodes.values().stream()
                .anyMatch(nodes -> nodes.stream().anyMatch(n -> n.getId().equals(nodeId)));
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

    private void clearScopingData() {
        getPayload().remove(MultiLevelModelStandardPayloadData.OriginalDetailLevelModel);
        getPayload().remove(MultiLevelModelStandardPayloadData.ImpactAnalysis);
        getPayload().remove(MultiLevelModelStandardPayloadData.ImpactAnalysisMaskingInstructions);
        getPayload().remove(MultiLevelModelStandardPayloadData.ScopedDetailLevelModel);
    }
}
