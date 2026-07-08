package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.common.BpmnComponentVariableType;
import org.rj.modelgen.bpmn.generation.BpmnConstants;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.ElementConnection;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnUIComponent;
import org.rj.modelgen.bpmn.intrep.model.assets.ElementNodeUnresolvedInput;
import org.rj.modelgen.bpmn.models.generation.multilevel.BpmnMultiLevelGenerationModel;
import org.rj.modelgen.bpmn.models.generation.validation.PayloadVariable;
import org.rj.modelgen.llm.component.ComponentInputResolutionStrategy;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.models.generation.multilevel.states.PrepareModelForRendering;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.util.Result;
import org.rj.modelgen.llm.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

import static org.rj.modelgen.bpmn.component.common.BpmnComponentInputSourceType.*;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.GatewayConstants.CONDITION_EXPRESSION;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.SUBPROCESS;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.PROCESS_CONFIG;
import static org.rj.modelgen.bpmn.intrep.model.common.ElementNodeSharedUtils.generateRandomId;
import static org.rj.modelgen.bpmn.models.generation.validation.BpmnScriptUtils.*;

public class PrepareBpmnModelForRendering extends PrepareModelForRendering {

    private static final Logger LOG = LoggerFactory.getLogger(PrepareBpmnModelForRendering.class);
    private static final List<String> NODES_TO_IGNORE = List.of(PROCESS_CONFIG, SUBPROCESS);

    private final BpmnGlobalVariableLibrary globalVariableLibrary;

    public PrepareBpmnModelForRendering(BpmnGlobalVariableLibrary globalVariableLibrary) {
        this(globalVariableLibrary, PrepareBpmnModelForRendering.class);
    }

    public PrepareBpmnModelForRendering(BpmnGlobalVariableLibrary globalVariableLibrary, Class<? extends PrepareModelForRendering> cls) {
        super(cls);
        this.globalVariableLibrary = globalVariableLibrary;
    }

    public PrepareBpmnModelForRendering withInputKeyOverride(String inputKeyOverride) {
        this.inputKeyOverride = inputKeyOverride;
        return this;
    }

    @Override
    protected Mono<Result<Void, String>> executeLogic() {
        final var findModel = getModelData();
        if (findModel.isErr()) return Mono.just(Result.Err(findModel.getError()));
        final var model = findModel.getValue();
        final var modelAssets = getModelAssets();

        final var componentLibrary = getComponentLibrary();
        if (componentLibrary == null) return Mono.just(Result.Err("Cannot prepare model for rendering; no component library available"));

        // Collect IDs of nodes targeted by boundary events — these are not orphans
        final Set<String> boundaryEventTargets = model.getNodes().stream()
                .filter(n -> n.getEvents() != null)
                .flatMap(n -> n.getEvents().stream())
                .filter(e -> e.getConnectedTo() != null)
                .flatMap(e -> e.getConnectedTo().stream())
                .map(org.rj.modelgen.bpmn.intrep.model.ElementConnection::getTargetNode)
                .collect(java.util.stream.Collectors.toSet());

        // Operations to be applied in order
        final List<Runnable> operations = new ArrayList<>(List.of(
                () -> removeInvalidNullNodes(model),
                () -> eliminateDuplicateConnections(model),
                () -> identifyOrphanedSubgraphs(model, node -> !NODES_TO_IGNORE.contains(node.getElementType())
                        && !boundaryEventTargets.contains(node.getId())),
                () -> resolveInputs(model),
                () -> assignStableIdsToArrayInputs(model),
                () -> addCustomOperations(model),
                () -> updateModelAssets(model, modelAssets)
        ));

        if (model.hasSubModels()) {
            for (var subModel : model.getSubModels()) {
                final var config = subModel.getSubProcessConfig();
                final String subModelName = config != null ? config.getSubProcessName() : "unknown";
                LOG.info("Preparing sub-model '{}' for rendering", subModelName);

                operations.add(() -> removeInvalidNullNodes(subModel));
                operations.add(() -> eliminateDuplicateConnections(subModel));
                operations.add(() -> resolveInputs(subModel));
                // skip orphan detection for event subprocesses.
                final boolean isEventSubprocess = config != null && config.isTriggeredByEvent();
                if (!isEventSubprocess) {
                    operations.add(() -> {
                        final Set<String> subBoundaryTargets = subModel.getNodes().stream()
                                .filter(n -> n.getEvents() != null)
                                .flatMap(n -> n.getEvents().stream())
                                .filter(e -> e.getConnectedTo() != null)
                                .flatMap(e -> e.getConnectedTo().stream())
                                .map(ElementConnection::getTargetNode)
                                .collect(Collectors.toSet());
                        subBoundaryTargets.addAll(boundaryEventTargets);
                        identifyOrphanedSubgraphs(subModel, node -> !NODES_TO_IGNORE.contains(node.getElementType()) && !subBoundaryTargets.contains(node.getId()));
                    });
                } else {
                    LOG.debug("Skipping orphan detection for event subprocess '{}'", subModelName);
                }
            }
        }

        return execute(model, modelAssets, operations);
    }

    // Identify any cases where a node is connected to another node via multiple edges, and remove the duplicate edges
    private void eliminateDuplicateConnections(BpmnIntermediateModel model) {
        for (final var node : model.getNodes()) {

            final var connections = node.getConnectedTo();
            // Make sure there are no duplicate connections within a group
            final Set<String> duplicates = new HashSet<>();
            if(connections == null) return;
            final var distinctConnections = connections.stream()
                    .filter(conn -> duplicates.add(conn.getTargetNode()))
                    .toList();
            if (distinctConnections.size() != connections.size()) {
                node.setConnectedTo(distinctConnections);
            }
        }
    }

    private void resolveInputs(BpmnIntermediateModel model) {
        for (final var node : model.getNodes()) {
            if (node.getInputs() == null) continue;
            for (final var input : node.getInputs()) {
                resolveInputValue(model, node, input);
            }
        }
        resolveProcessConfigNodeId(model);
    }

    private void resolveInputValue(BpmnIntermediateModel model, ElementNode node, ElementNodeInput input) {
        // If the input has properties, resolve each property recursively
        if (input.hasProperties()) {
            for (var prop : input.getProperties()) {
                resolveInputValue(model, node, prop);
            }
        } else {
            var inputDefinition = getComponentLibrary()
                    .getComponentByName(node.getElementType())
                    .flatMap(component -> component.getInputVariable(input.getName()));

            var inputValue = input.getValue();
            var inputSource = input.getVariableSource();

            // If input is not provided but input definition has a default value, use it only if resolution strategy requires user involvement
            // Otherwise, keep the inferred value
            if (Boolean.FALSE.equals(input.getIsProvided())
                    && inputDefinition.isPresent() && inputDefinition.get().getDefaultValue() != null
                    && inputDefinition.get().getResolutionStrategy().requiresUserInvolvement()) {
                inputValue = inputDefinition.get().getDefaultValue();
            }

            if (SCRIPT.toString().equals(inputSource)) {
                inputValue = resolveVariableWrites(inputValue);
                inputValue = resolveVariableReads(inputValue, getComponentLibrary(), false);
                inputValue = resolveGlobalVariableReads(inputValue, globalVariableLibrary, false);

                // Hook for subclasses to add custom post-processing
                inputValue = postProcessScriptInput(input, inputValue);
            }

            if (EXPRESSION.toString().equals(inputSource)) {
                // Gateway condition expressions use JUEL syntax (e.g., ${x > 5}), so variable references must not be interpolated; all other expressions require interpolation
                boolean isConditionExpr = CONDITION_EXPRESSION.equals(input.getName());
                inputValue = resolveVariableReads(inputValue, getComponentLibrary(), !isConditionExpr);
                inputValue = resolveGlobalVariableReads(inputValue, globalVariableLibrary, !isConditionExpr);
                inputValue = stripQuotes(inputValue);
                if (isConditionExpr && !inputValue.isBlank()) {
                    inputValue = "${" + inputValue + "}";
                }
            }

            input.setValue(inputValue);
        }
    }


    private void resolveProcessConfigNodeId(BpmnIntermediateModel model) {
        model.getNodes().stream()
                .filter(node -> PROCESS_CONFIG.equalsIgnoreCase(node.getElementType()))
                .findFirst()
                .ifPresent(node -> node.findInput(BpmnConstants.ProcessConfigConstants.PROCESS_ID)
                        .map(ElementNodeInput::getValue)
                        .filter(pid -> !pid.isBlank())
                        .ifPresent(node::setId));
    }

    private void assignStableIdsToArrayInputs(BpmnIntermediateModel model) {
        var componentLibrary = getComponentLibrary();
        if (componentLibrary == null) return;

        for (var node : model.getNodes()) {
            if (node.getInputs() == null || node.getInputs().isEmpty()) continue;
            var component = componentLibrary.getComponentByName(node.getElementType()).orElse(null);
            if (component == null) continue;

            String stableIdName = node.getUniqueElementIdName();
            ensureStableIdsAssigned(node.getInputs(), component, stableIdName);
        }
    }

    private static void ensureStableIdsAssigned(List<ElementNodeInput> inputs, BpmnComponent component, String stableIdName) {
        if (inputs == null || stableIdName == null) return;
        for (var input : inputs) {
            if (input.hasProperties()) {
                boolean isArrayType = component.getInputVariable(input.getName())
                        .map(iv -> iv.getType() == BpmnComponentVariableType.Array)
                        .orElse(false);

                if (isArrayType && input.findProperty(stableIdName).isEmpty()) {
                    var updatedProperties = new ArrayList<>(input.getProperties());
                    updatedProperties.add(ElementNodeInput.createConstant(stableIdName, generateRandomId()));
                    input.setProperties(updatedProperties);
                }

                for (var prop : input.getProperties()) {
                    if (prop.hasProperties()) {
                        ensureStableIdsAssigned(List.of(prop), component, stableIdName);
                    }
                }
            }
        }
    }

    // Hook for subclasses to perform additional processing on the model after input resolution but before model assets are updated
    protected void addCustomOperations(BpmnIntermediateModel model) {
        // No-op in base class; override in subclasses
    }

    private void updateModelAssets(BpmnIntermediateModel model, BpmnModelAssets modelAssets) {
        Collection<BpmnUIComponent> uiComponentsRaw = getPayload().get(MultiLevelModelStandardPayloadData.UIComponents);
        List<BpmnUIComponent> uiComponents = modelAssets.getUiComponents() != null ? modelAssets.getUiComponents() : new ArrayList<>();
        if (uiComponentsRaw != null) {
            uiComponents = new ArrayList<>(uiComponentsRaw); // Update UI components, if available
        }

        List<ElementNodeUnresolvedInput> unresolvedInputs = identifyUnresolvedInputs(model);

        Collection<PayloadVariable> processVarsRaw = getPayload().get(MultiLevelModelStandardPayloadData.ProcessVariables);
        List<PayloadVariable> startingPayload = modelAssets.getStartingPayload() != null ? modelAssets.getStartingPayload() : new ArrayList<>();
        if (processVarsRaw != null) {
            startingPayload = processVarsRaw.stream().toList();
        }

        modelAssets.setStartingPayload(startingPayload);
        modelAssets.setUiComponents(uiComponents);
        modelAssets.setUnresolvedInputs(unresolvedInputs);
        getPayload().put(StandardModelData.ModelAssets.toString(), modelAssets);
    }

    private List<ElementNodeUnresolvedInput> identifyUnresolvedInputs(BpmnIntermediateModel model) {
        List<ElementNodeUnresolvedInput> unresolvedInputs = new ArrayList<>();
        for (final var node : model.getNodes()) {
            if (node.getInputs() == null) continue;
            var component = getComponentLibrary()
                    .getComponentByName(node.getElementType());
            if (component.isEmpty()) continue;

            String uniqueIdName = node.getUniqueElementIdName();

            for (final var input : node.getInputs()) {
                String inputKey = buildInputKeySegment(input, component.get(), uniqueIdName);
                identifyUnresolvedInputsProperties(component.get(), node, input, inputKey, uniqueIdName, unresolvedInputs);
            }
        }
        return unresolvedInputs;
    }


    private void identifyUnresolvedInputsProperties(BpmnComponent component, ElementNode node, ElementNodeInput input, String inputKey, String uniqueIdPropertyName, List<ElementNodeUnresolvedInput> unresolvedInputs) {
        if (input.hasProperties()) {
            for (var prop : input.getProperties()) {
                String propSegment = buildInputKeySegment(prop, component, uniqueIdPropertyName);
                identifyUnresolvedInputsProperties(component, node, prop, inputKey + "." + propSegment, uniqueIdPropertyName, unresolvedInputs);
            }
        } else if (Boolean.FALSE.equals(input.getIsProvided())) {
            var inputDefinition = component.getInputVariable(input.getName());
            if (inputDefinition.isEmpty()) return;

            ComponentInputResolutionStrategy resolutionStrategy = inputDefinition.get().getResolutionStrategy();
            if(resolutionStrategy == null) return;
            
            String defaultValue = inputDefinition.get().getDefaultValue();
            String alias = Optional.ofNullable(inputDefinition.get().getAlias()).orElse(input.getName());

            if (resolutionStrategy.requiresUserInvolvement()) {
                unresolvedInputs.add(new ElementNodeUnresolvedInput(node.getId(), node.getElementType(), input.getName(), alias, input.getValue(), defaultValue, inputKey, resolutionStrategy));
            }
        }
    }

    protected String postProcessScriptInput(ElementNodeInput input, String inputValue) {
        inputValue = resolveErrorThrows(inputValue, "throw new Exception($1)");
        return inputValue;
    }

    private String getModelKey() {
        return Optional.ofNullable(inputKeyOverride).orElse(MultiLevelModelStandardPayloadData.DetailLevelModel.toString());
    }

    protected BpmnComponentLibrary getComponentLibrary() {
        return Optional.ofNullable(getModel())
                .map(m -> m.getAs(BpmnMultiLevelGenerationModel.class))
                .map(BpmnMultiLevelGenerationModel::getComponentLibrary)
                .orElse(null);
    }

    private Result<BpmnIntermediateModel, String> getModelData() {
        return Optional.ofNullable(getPayload().<String>get(getModelKey()))
                .map(serialized -> Util.tryDeserialize(serialized, BpmnIntermediateModel.class))
                .map(res -> res.mapErr(Exception::toString))
                .orElseGet(() -> Result.Err("No valid input model found"));
    }

    private BpmnModelAssets getModelAssets() {
        final var existingAssets = getPayload().<BpmnModelAssets>get(StandardModelData.ModelAssets.toString());
        if (existingAssets != null) {
            return existingAssets;
        }
        // Otherwise, create and persist a new model assets object
        final var assets = new BpmnModelAssets();
        getPayload().put(StandardModelData.ModelAssets.toString(), assets);
        return assets;
    }

    @Override
    public String getDescription() {
        return "Clean up BPMN model for rendering";
    }

}
