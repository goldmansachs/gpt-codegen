package org.rj.modelgen.bpmn.subproblem;

import org.json.JSONObject;
import org.rj.modelgen.bpmn.component.synthetic.types.BpmnSyntheticTerminateWorkflowNode;
import org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;
import org.rj.modelgen.bpmn.intrep.model.SubProcessConfig;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;
import org.rj.modelgen.bpmn.intrep.model.assets.ElementNodeUnresolvedInput;
import org.rj.modelgen.bpmn.models.generation.validation.PayloadVariable;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.subproblem.data.SubproblemDecomposition;
import org.rj.modelgen.llm.subproblem.data.SubproblemDetails;
import org.rj.modelgen.llm.subproblem.states.CombineSubproblems;
import org.rj.modelgen.llm.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.isEndEventType;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.isStartEventType;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.*;

public class BpmnCombineSubproblems extends CombineSubproblems {
    private static final Logger LOG = LoggerFactory.getLogger(BpmnCombineSubproblems.class);

    private static final Pattern BOILERPLATE_PREFIX = Pattern.compile("^\\s*(?:Done)!?\\s*", Pattern.CASE_INSENSITIVE);

    public BpmnCombineSubproblems() {
        super(BpmnCombineSubproblems.class);
    }

    @Override
    protected Result<String, String> combineSubproblems(List<SubproblemDetails> subproblems) {
        final var result = buildCompoundModel(subproblems)
                .map(BpmnIntermediateModel::serialize);

        if (result.isOk()) {
            combineModelAssets(subproblems.size());
        }

        return result;
    }

    private Result<BpmnIntermediateModel, String> buildCompoundModel(List<SubproblemDetails> subproblems) {
        if (subproblems == null || subproblems.isEmpty()) {
            return Result.Err("No valid subproblems to recombine");
        }

        // 1. parse the main process (subprocess 0)
        final BpmnIntermediateModel model = parseMainProcess(subproblems.get(0));

        // 2. attach each subprocess sub-model (subprocess 1..n)
        final var attachResult = attachSubprocessModels(model, subproblems);
        if (attachResult.isErr()) return Result.Err(attachResult.getError());

        // 3. post-processing: connect process to subprocess, inject synthetic nodes, validation
        reconcileSubprocessReferences(model);
        BpmnSyntheticTerminateWorkflowNode.addTerminateWorkflowSubprocess(model);
        warnOnUnresolvedDependencies(model);

        return Result.Ok(model);
    }

    private BpmnIntermediateModel parseMainProcess(SubproblemDetails primary) {
        final BpmnIntermediateModel model = BpmnIntermediateModel.fromJson(
                new JSONObject(primary.result()));
        model.setSubProcessConfig(null);
        return model;
    }

    private Result<Void, String> attachSubprocessModels(BpmnIntermediateModel model,
                                                        List<SubproblemDetails> subproblems) {
        final Set<String> mainNodeIds = model.getNodes().stream()
                .map(ElementNode::getId)
                .collect(Collectors.toSet());

        final StringBuilder commentary = new StringBuilder();
        if (model.getCommentary() != null) {
            commentary.append(model.getCommentary());
        }

        for (int i = 1; i < subproblems.size(); ++i) {
            final String subprocessId = resolveSubprocessId(i);
            final BpmnIntermediateModel subprocess = BpmnIntermediateModel.fromJson(
                    new JSONObject(subproblems.get(i).result()));

            final var validationResult = validateSubprocess(subprocess, mainNodeIds);
            if (validationResult.isErr()) {
                return Result.Err("Subprocess validation failed for '%s': %s"
                        .formatted(subprocessId, validationResult.getError()));
            }

            appendCommentary(commentary, subprocessId, subprocess);
            initSubProcessConfig(subprocess, subprocessId);
            model.getSubModels().add(subprocess);
        }

        if (!commentary.isEmpty()) model.setCommentary(commentary.toString());
        return Result.Ok();
    }

    private String resolveSubprocessId(int subproblemIndex) {
        final var key = SubproblemDecomposition.getSubproblemToSubprocessDataKey(subproblemIndex);
        return getPayload() != null ? getPayload().getOrElse(key, (String) null) : null;
    }

    private void appendCommentary(StringBuilder target, String subprocessId,
                                  BpmnIntermediateModel subprocess) {
        if (subprocess.getCommentary() == null || subprocess.getCommentary().isBlank()) return;

        String cleaned = stripBoilerplate(subprocess.getCommentary());
        if (cleaned.isEmpty()) return;

        String label = (subprocessId != null && !subprocessId.isBlank()) ? "Subprocess \"%s\"".formatted(subprocessId) : "Subprocess";
        target.append("\n\n").append(label).append(": ").append(cleaned);
        subprocess.setCommentary(null);
    }

    private static String stripBoilerplate(String text) {
        if (text == null || text.isBlank()) return "";
        return BOILERPLATE_PREFIX.matcher(text).replaceFirst("").trim();
    }

    private void initSubProcessConfig(BpmnIntermediateModel subprocess, String decompositionId) {
        final SubProcessConfig config = subprocess.getOrCreateSubProcessConfig();
        config.applyDefaults(decompositionId);

        // Force subprocess ID to the decomposition tag (e.g. "SP1"). This is the only
        // stable identifier shared between the independently-generated main process and
        // subprocess, so it must always win over whatever the LLM chose.
        if (decompositionId != null) {
            config.setSubProcessId(decompositionId);
        }
    }

    private void reconcileSubprocessReferences(BpmnIntermediateModel model) {
        if (!model.hasSubModels()) return;

        for (BpmnIntermediateModel subModel : model.getSubModels()) {
            SubProcessConfig config = subModel.getSubProcessConfig();
            if (config == null || config.isTriggeredByEvent()) continue;

            String spId = config.getSubProcessId();
            if (spId == null) continue;

            boolean hasCallNode = model.getNodes().stream()
                    .filter(BpmnCombineSubproblems::isInlineSubProcessCallNode)
                    .anyMatch(node -> spId.equals(
                            node.findInput(SUBPROCESS_ID).map(ElementNodeInput::getValue).orElse(null)));

            if (hasCallNode) {
                LOG.info("Matched inline subprocess '{}' to call node in main process", spId);
            } else {
                LOG.warn("No inline subProcess call node with subProcessId='{}' found in main process. Subprocess will be rendered as a standalone embedded subprocess", spId);
            }
        }
    }

    private void warnOnUnresolvedDependencies(BpmnIntermediateModel model) {
        final var result = model.validateDependencies();
        if (result.isErr()) {
            LOG.warn("Dependency validation warning (partial combination): {}", result.getError());
        }
    }

    private Result<Void, String> validateSubprocess(BpmnIntermediateModel subprocess,
                                                    Set<String> mainNodeIds) {
        if (subprocess == null) return Result.Err("Subprocess model is null");

        stripInvalidNodeTypes(subprocess);
        deduplicateMainProcessNodes(subprocess, mainNodeIds);

        boolean hasStart = subprocess.getNodes().stream().anyMatch(n -> isStartEventType(n.getElementType()));
        boolean hasEnd = subprocess.getNodes().stream().anyMatch(n -> isEndEventType(n.getElementType()));

        if (!hasStart) return Result.Err("Subprocess has no start event");
        if (!hasEnd)   return Result.Err("Subprocess has no end event");

        return Result.Ok();
    }

    private void stripInvalidNodeTypes(BpmnIntermediateModel subprocess) {
        subprocess.getNodes().removeIf(node -> {
            if (node == null || node.getElementType() == null) return true;

            if (NodeTypes.PROCESS_CONFIG.equals(node.getElementType())) {
                LOG.warn("Removing invalid processConfig node '{}' from subprocess", node.getName());
                return true;
            }
            if (SUBPROCESS.equals(node.getElementType())) {
                LOG.info("Extracting config from subProcess node '{}' before removing", node.getName());
                final var config = subprocess.getOrCreateSubProcessConfig();
                config.populateFromNode(node);
                if (config.getSubProcessName() == null && node.getName() != null) {
                    config.setSubProcessName(node.getName());
                }
                if (config.getSubProcessId() == null && node.getId() != null) {
                    config.setSubProcessId(node.getId());
                }
                return true;
            }
            return false;
        });
    }

    private void deduplicateMainProcessNodes(BpmnIntermediateModel subprocess,
                                             Set<String> mainNodeIds) {
        int before = subprocess.getNodes().size();
        subprocess.getNodes().removeIf(n -> mainNodeIds.contains(n.getId()));
        int removed = before - subprocess.getNodes().size();
        if (removed > 0) {
            LOG.warn("Removed {} node(s) from subprocess that duplicated main process IDs", removed);
        }
    }

    private static boolean isInlineSubProcessCallNode(ElementNode node) {
        return SUBPROCESS.equals(node.getElementType())
                && node.getConnectedTo() != null
                && !node.getConnectedTo().isEmpty();
    }

    // Combines model assets (unresolved inputs, starting payload)
    private void combineModelAssets(int subproblemCount) {
        List<ElementNodeUnresolvedInput> combinedUnresolvedInputs = new ArrayList<>();
        List<PayloadVariable> combinedStartingPayload = null;

        for (int i = 0; i < subproblemCount; i++) {
            final Object raw = getPayload().getData().get(subproblemAssetsKey(i));
            if (!(raw instanceof BpmnModelAssets assets)) continue;

            // Unresolved inputs: accumulate from every subproblem
            if (assets.getUnresolvedInputs() != null) {
                combinedUnresolvedInputs.addAll(assets.getUnresolvedInputs());
            }

            // Starting payload: take from the main process (subproblem 0), which is the process-level payload
            if (i == 0 && assets.getStartingPayload() != null) {
                combinedStartingPayload = new ArrayList<>(assets.getStartingPayload());
            }
        }

        final BpmnModelAssets combinedAssets = new BpmnModelAssets();
        combinedAssets.setUnresolvedInputs(combinedUnresolvedInputs);
        combinedAssets.setStartingPayload(combinedStartingPayload);

        getPayload().getData().put(StandardModelData.ModelAssets.toString(), combinedAssets);

        LOG.info("Combined model assets from {} subproblems: {} unresolved inputs, {} starting payload variables", subproblemCount, combinedUnresolvedInputs.size(), combinedStartingPayload != null ? combinedStartingPayload.size() : 0);
    }
}
