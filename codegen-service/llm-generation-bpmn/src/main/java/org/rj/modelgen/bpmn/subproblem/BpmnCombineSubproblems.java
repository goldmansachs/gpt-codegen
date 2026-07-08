package org.rj.modelgen.bpmn.subproblem;

import org.json.JSONObject;
import org.rj.modelgen.bpmn.component.synthetic.types.BpmnSyntheticTerminateWorkflowNode;
import org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.ElementConnection;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;
import org.rj.modelgen.bpmn.intrep.model.SubProcessConfig;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnUIComponent;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.models.generation.multilevel.MultiLevelGenerationModelStates;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.subproblem.data.SubproblemDecomposition;
import org.rj.modelgen.llm.subproblem.data.SubproblemDetails;
import org.rj.modelgen.llm.subproblem.states.CombineSubproblems;
import org.rj.modelgen.llm.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.IS_INTERRUPTING;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.MESSAGE_REF;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.GatewayConstants.DEFAULT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.*;
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
        final var mainProcess = subproblems.get(0);
        final BpmnIntermediateModel model = parseMainProcess(mainProcess);

        // 2. attach each subprocess sub-model (subprocess 1..n)
        final var attachResult = attachSubprocessModels(model, subproblems, mainProcess);
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

    private Result<Void, String> attachSubprocessModels(BpmnIntermediateModel model, List<SubproblemDetails> subproblems, SubproblemDetails mainProcess) {
        final StringBuilder commentary = new StringBuilder();
        if (model.getCommentary() != null) {
            commentary.append(model.getCommentary());
        }

        for (SubproblemDetails details : subproblems) {
            if (details == mainProcess) continue;   // already used as the main process

            final String subprocessId = resolveSubprocessId(details.subproblemId());
            final BpmnIntermediateModel subprocess = BpmnIntermediateModel.fromJson(
                    new JSONObject(details.result()));

            final var validationResult = validateSubprocess(subprocess);
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

        final List<ElementNode> callNodes = model.getNodes().stream()
                .filter(ElementNode::isSubprocessCallNode)
                .collect(Collectors.toList());
        final Set<String> claimedCallNodeIds = new HashSet<>();

        final List<BpmnIntermediateModel> subModels = model.getSubModels().stream()
                .filter(sm -> sm.getSubProcessConfig() != null
                        && sm.getSubProcessConfig().getSubProcessId() != null)
                .sorted(Comparator.comparing(sm -> sm.getSubProcessConfig().getSubProcessId()))
                .collect(Collectors.toList());

        // Pass 1: match each sub-model to its call node(s) by subProcessId / normalized id / node id.
        final List<BpmnIntermediateModel> unmatchedInline = new ArrayList<>();
        for (BpmnIntermediateModel subModel : subModels) {
            final SubProcessConfig config = subModel.getSubProcessConfig();
            final String spId = config.getSubProcessId();

            final List<ElementNode> matches = findAllMatchingCallNodes(callNodes, claimedCallNodeIds, spId);
            if (matches.isEmpty()) {
                // event sub-models never need a call node
                if (!config.isTriggeredByEvent()) {
                    unmatchedInline.add(subModel);
                }
                continue;
            }

            final ElementNode primary = matches.get(0);
            claimedCallNodeIds.add(primary.getId());

            if (config.isTriggeredByEvent()) {
                config.setTriggeredByEvent(false);
            }

            final String currentInputValue = primary.findInput(SUBPROCESS_ID)
                    .map(ElementNodeInput::getValue).orElse(null);
            if (!spId.equals(currentInputValue)) {
                setSubProcessIdInput(primary, spId);
            } else {
                LOG.info("Matched inline subprocess '{}' to call node '{}' in main process", spId, primary.getId());
            }

            for (int i = 1; i < matches.size(); i++) {
                final ElementNode secondary = matches.get(i);
                claimedCallNodeIds.add(secondary.getId());
                mergeSecondaryCallNode(model, secondary, primary, spId);
            }
        }

        for (BpmnIntermediateModel subModel : unmatchedInline) {
            convertToEventSubprocess(subModel);
        }
    }

    // Converts an inline subprocess with no call node in the main process into a event subprocess
    // if its start is a plain startEvent, convert it to a messageStartEvent
    private void convertToEventSubprocess(BpmnIntermediateModel subModel) {
        final SubProcessConfig config = subModel.getSubProcessConfig();
        final String spId = config != null ? config.getSubProcessId() : null;
        if (config != null) {
            config.setTriggeredByEvent(true);
        }

        subModel.getNodes().stream()
                .filter(n -> isStartEventType(n.getElementType()))
                .findFirst()
                .filter(n -> START_EVENT.equals(n.getElementType()))
                .ifPresent(startNode -> {
                    startNode.setElementType(MESSAGE_START_EVENT);
                    final List<ElementNodeInput> inputs = startNode.getInputs() != null
                            ? new ArrayList<>(startNode.getInputs()) : new ArrayList<>();
                    if (startNode.findInput(IS_INTERRUPTING).isEmpty()) {
                        inputs.add(ElementNodeInput.createConstant(IS_INTERRUPTING, "true"));
                    }
                    if (startNode.findInput(MESSAGE_REF).isEmpty()) {
                        inputs.add(ElementNodeInput.createConstant(MESSAGE_REF, "triggerEvent" + spId));
                    }
                    startNode.setInputs(inputs);
                });

        LOG.warn("Inline subprocess '{}' has no matching subprocessCallNode in the main process; converting it to a message-triggered event subprocess", spId);
    }

    private static List<ElementNode> findAllMatchingCallNodes(List<ElementNode> callNodes, Set<String> claimed, String spId) {
        final Set<String> seen = new LinkedHashSet<>();
        final List<ElementNode> result = new ArrayList<>();
        for (ElementNode node : callNodes) {
            if (claimed.contains(node.getId()) || seen.contains(node.getId())) continue;
            final String inputVal = node.findInput(SUBPROCESS_ID).map(ElementNodeInput::getValue).orElse(null);
            if (spId.equals(inputVal) || spId.equals(node.getId())) {
                result.add(node);
                seen.add(node.getId());
            }
        }
        return result;
    }

    private static void mergeSecondaryCallNode(BpmnIntermediateModel model, ElementNode secondary,
                                               ElementNode primary, String spId) {
        for (ElementNode node : model.getNodes()) {
            if (node == secondary) continue;
            if (node.getConnectedTo() != null) {
                node.getConnectedTo().forEach(conn -> {
                    if (secondary.getId().equals(conn.getTargetNode())) {
                        conn.setTargetNode(primary.getId());
                    }
                });
            }
            if (node.getInputs() != null) {
                node.getInputs().stream()
                        .filter(inp -> DEFAULT.equals(inp.getName())
                                && secondary.getId().equals(inp.getValue()))
                        .forEach(inp -> inp.setValue(primary.getId()));
            }
        }

        // repoint the into same subprocess
        if (secondary.getConnectedTo() != null && !secondary.getConnectedTo().isEmpty()) {
            final List<ElementConnection> merged = new ArrayList<>(primary.getConnectedTo());
            merged.addAll(secondary.getConnectedTo());
            primary.setConnectedTo(merged);
        }

        model.getNodes().removeIf(n -> secondary.getId().equals(n.getId()));
        LOG.info("Subprocess '{}': merged call node '{}' into primary '{}'", spId, secondary.getId(), primary.getId());
    }

    private static void setSubProcessIdInput(ElementNode callNode, String spId) {
        final Optional<ElementNodeInput> existing = callNode.findInput(SUBPROCESS_ID);
        if (existing.isPresent()) {
            existing.get().setValue(spId);
            return;
        }
        final ElementNodeInput input = new ElementNodeInput();
        input.setName(SUBPROCESS_ID);
        input.setValue(spId);
        final List<ElementNodeInput> inputs = callNode.getInputs() != null
                ? new ArrayList<>(callNode.getInputs())
                : new ArrayList<>();
        inputs.add(input);
        callNode.setInputs(inputs);
    }

    private void warnOnUnresolvedDependencies(BpmnIntermediateModel model) {
        final var result = model.validateDependencies();
        if (result.isErr()) {
            LOG.warn("Dependency validation warning (partial combination): {}", result.getError());
        }
    }

    private Result<Void, String> validateSubprocess(BpmnIntermediateModel subprocess) {
        if (subprocess == null) return Result.Err("Subprocess model is null");

        stripInvalidNodeTypes(subprocess);

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

    // Combines model assets from all subproblems into a single authoritative BpmnModelAssets.
    // Delegates deduplication semantics to BpmnModelAssets.merge(List<>).
    private void combineModelAssets(int subproblemCount) {
        final List<BpmnModelAssets> perSubproblemAssets = new ArrayList<>();
        for (int i = 0; i < subproblemCount; i++) {
            final Object raw = getPayload().getData().get(subproblemAssetsKey(i));
            if (raw instanceof BpmnModelAssets assets) {
                perSubproblemAssets.add(assets);
            }
        }

        final BpmnModelAssets combinedAssets = new BpmnModelAssets();
        perSubproblemAssets.forEach(combinedAssets::merge);

        // Deduplicate unresolved inputs by (nodeId, inputKey)
        if (combinedAssets.getUnresolvedInputs() != null) {
            final var seen = new java.util.LinkedHashSet<String>();
            final var deduped = combinedAssets.getUnresolvedInputs().stream()
                    .filter(u -> seen.add(u.getNodeId() + ":" + u.getInputKey()))
                    .toList();
            combinedAssets.setUnresolvedInputs(new ArrayList<>(deduped));
        }

        getPayload().getData().put(StandardModelData.ModelAssets.toString(), combinedAssets);

        final List<BpmnUIComponent> uiComponents = combinedAssets.getUiComponents();
        if (uiComponents != null && !uiComponents.isEmpty()) {
            // Publish UIComponents to the payload key UIGeneration reads
            getPayload().put(MultiLevelModelStandardPayloadData.UIComponents, uiComponents);
            // Explicit flag so UIGeneration does not need to inspect content to decide whether to skip
            getPayload().put(MultiLevelGenerationModelStates.SubproblemUIGenerationComplete, Boolean.TRUE);
        }

        LOG.info("Combined model assets from {} subproblems: {} unresolved inputs, {} starting payload variables, {} UI components",
                subproblemCount,
                combinedAssets.getUnresolvedInputs() != null ? combinedAssets.getUnresolvedInputs().size() : 0,
                combinedAssets.getStartingPayload() != null ? combinedAssets.getStartingPayload().size() : 0,
                uiComponents != null ? uiComponents.size() : 0);
    }
}
