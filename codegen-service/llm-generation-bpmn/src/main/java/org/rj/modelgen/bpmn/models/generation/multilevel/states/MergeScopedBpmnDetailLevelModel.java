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

        // Build lookup of generated (affected) nodes by ID
        Map<String, ElementNode> generatedNodesById = generatedModel.getNodes().stream()
                .collect(Collectors.toMap(ElementNode::getId, Function.identity(), (a, b) -> b));

        String generatedCommentary = generatedModel.getCommentary();
        generatedModel.setCommentary(null); // Strip original commentary to save tokens
        getPayload().put(MultiLevelModelStandardPayloadData.ScopedDetailLevelModel, generatedModel.serialize());
        generatedModel.setCommentary(generatedCommentary);

        List<ElementNode> mergedNodes = mergeNodeLists(originalModel.getNodes(), generatedNodesById, impactAnalysis);
        int restoredCount = (int) mergedNodes.stream()
                .filter(node -> !generatedNodesById.containsKey(node.getId()))
                .count();

        generatedModel.setNodes(mergedNodes);
        if (originalModel.getCommentary() != null && !originalModel.getCommentary().isBlank()) {
            generatedModel.setCommentary(originalModel.getCommentary());
        }
        LOG.info("Merge complete: {} unaffected nodes restored, {} affected from generation. Final: {} nodes.", restoredCount, generatedNodesById.size(), mergedNodes.size());

        // Store merged model and clear scoping data for a fresh cycle
        final String mergedModelContent = generatedModel.serialize();
        getPayload().put(MultiLevelModelStandardPayloadData.DetailLevelModel, mergedModelContent);
        recordAudit("full-dl", mergedModelContent);
        clearScopingData();
    }

    private List<ElementNode> mergeNodeLists(List<ElementNode> originalNodes, Map<String, ElementNode> generatedNodesById, ImpactAnalysisResult impactAnalysis) {

        Set<String> removeIds = new HashSet<>(impactAnalysis.getRemoveNodeIds());

        // Any affected node NOT returned by the LLM was implicitly replaced (e.g. split/renamed).
        // Treat it as removed so the stale original node doesn't remain in the merged model.
        for (String affectedId : impactAnalysis.getAffectedNodeIds()) {
            if (!generatedNodesById.containsKey(affectedId)) {
                removeIds.add(affectedId);
                LOG.info("Affected node '{}' not returned by LLM during scoped retry — treating as implicitly removed/replaced", affectedId);
            }
        }

        // Merge: use generated version for affected nodes, original for unaffected, skip removed
        List<ElementNode> mergedNodes = new ArrayList<>();
        Set<String> processedIds = new HashSet<>();

        for (ElementNode originalNode : originalNodes) {
            String id = originalNode.getId();
            if (removeIds.contains(id)) continue;
            mergedNodes.add(generatedNodesById.getOrDefault(id, originalNode));
            processedIds.add(id);
        }

        // Add any new nodes from the generated model
        for (Map.Entry<String, ElementNode> entry : generatedNodesById.entrySet()) {
            if (!processedIds.contains(entry.getKey())) {
                mergedNodes.add(entry.getValue());
            }
        }

        // Sanitize dangling connections to removed nodes
        if (!removeIds.isEmpty()) {
            int danglingRemoved = sanitizeConnections(mergedNodes, removeIds);
            if (danglingRemoved > 0) {
                LOG.warn("Removed {} dangling connection(s) referencing removed node IDs: {}", danglingRemoved, removeIds);
            }
        }

        return mergedNodes;
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

