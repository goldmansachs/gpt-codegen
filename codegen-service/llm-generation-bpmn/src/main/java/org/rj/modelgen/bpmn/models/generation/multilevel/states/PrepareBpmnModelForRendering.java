package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
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
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.PROCESS_CONFIG;
import static org.rj.modelgen.bpmn.models.generation.validation.BpmnScriptUtils.*;

public class PrepareBpmnModelForRendering extends PrepareModelForRendering {

    private static final Logger LOG = LoggerFactory.getLogger(PrepareBpmnModelForRendering.class);
    private static final List<String> NODES_TO_IGNORE = List.of(PROCESS_CONFIG);

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

        // Operations to be applied in order
        final List<Runnable> operations = List.of(
                () -> removeInvalidNullNodes(model),
                () -> eliminateDuplicateConnections(model),
                () -> identifyOrphanedSubgraphs(model, node -> !NODES_TO_IGNORE.contains(node.getElementType())),
                () -> resolveInputs(model),
                () -> updateModelAssets(model, modelAssets)
        );

        return execute(model, modelAssets, operations);
    }

    // Identify any cases where a node is connected to another node via multiple edges, and remove the duplicate edges
    private void eliminateDuplicateConnections(BpmnIntermediateModel model) {
        for (final var node : model.getNodes()) {

            final var connections = node.getConnectedTo();
            // Make sure there are no duplicate connections within a group
            final Set<String> duplicates = new HashSet<>();
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
            if (!input.getIsProvided()
                    && inputDefinition.isPresent() && inputDefinition.get().getDefaultValue() != null
                    && inputDefinition.get().getResolutionStrategy().requiresUserInvolvement()) {
                inputValue = inputDefinition.get().getDefaultValue();
            }

            if (inputSource.equals(SCRIPT.toString())) {
                inputValue = resolveVariableWrites(inputValue);
                inputValue = resolveVariableReads(inputValue, getComponentLibrary(), false);
                inputValue = resolveGlobalVariableReads(inputValue, globalVariableLibrary, false);

                // Hook for subclasses to add custom post-processing
                inputValue = postProcessScriptInput(input, inputValue);
            }

            if (inputSource.equals(EXPRESSION.toString())) {
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

    private void updateModelAssets(BpmnIntermediateModel model, BpmnModelAssets modelAssets) {
        Collection<BpmnUIComponent> uiComponentsRaw = getPayload().get(MultiLevelModelStandardPayloadData.UIComponents);
        List<BpmnUIComponent> uiComponents = uiComponentsRaw != null ? new ArrayList<>(uiComponentsRaw) : new ArrayList<>();
        List<ElementNodeUnresolvedInput> unresolvedInputs = identifyUnresolvedInputs(model);
        Collection<PayloadVariable> processVarsRaw = getPayload().get(MultiLevelModelStandardPayloadData.ProcessVariables);
        List<PayloadVariable> startingPayload = processVarsRaw != null ? new ArrayList<>(processVarsRaw) : new ArrayList<>();

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

            for (final var input : node.getInputs()) {
                String path = input.getName();
                identifyUnresolvedInputsProperties(component.get(), node, input, path, unresolvedInputs);
            }
        }
        return unresolvedInputs;
    }

    private void identifyUnresolvedInputsProperties(BpmnComponent component, ElementNode node, ElementNodeInput input, String path, List<ElementNodeUnresolvedInput> unresolvedInputs) {
        if (input.hasProperties()) {
            for (var prop : input.getProperties()) {
                identifyUnresolvedInputsProperties(component, node, prop, path + "." + prop.getName(), unresolvedInputs);
            }
        } else if (!input.getIsProvided()) {
            var inputDefinition = component.getInputVariable(input.getName());
            if (inputDefinition.isEmpty()) return;

            ComponentInputResolutionStrategy resolutionStrategy = inputDefinition.get().getResolutionStrategy();
            if(resolutionStrategy == null) return;
            
            String defaultValue = inputDefinition.get().getDefaultValue();
            String alias = Optional.ofNullable(inputDefinition.get().getAlias()).orElse(input.getName());

            if (resolutionStrategy.requiresUserInvolvement()) {
                unresolvedInputs.add(new ElementNodeUnresolvedInput(node.getId(), node.getElementType(), input.getName(), alias, input.getValue(), defaultValue, path, resolutionStrategy));
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

    private BpmnComponentLibrary getComponentLibrary() {
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
