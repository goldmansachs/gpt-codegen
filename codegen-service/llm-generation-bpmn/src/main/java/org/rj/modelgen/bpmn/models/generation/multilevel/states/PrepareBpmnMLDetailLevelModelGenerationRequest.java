package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.models.generation.base.context.BpmnPromptPlaceholders;
import org.rj.modelgen.bpmn.models.generation.multilevel.data.ImpactAnalysisResult;
import org.rj.modelgen.llm.component.ComponentLibrary;
import org.rj.modelgen.llm.context.Context;
import org.rj.modelgen.llm.intrep.IntermediateModelParser;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.models.generation.multilevel.states.PrepareAndSubmitMLRequestForLevelParams;
import org.rj.modelgen.llm.prompt.PromptSubstitution;
import org.rj.modelgen.llm.prompt.StandardPromptPlaceholders;
import org.rj.modelgen.llm.schema.ModelSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class PrepareBpmnMLDetailLevelModelGenerationRequest<TComponentLibrary extends ComponentLibrary<?>>
        extends PrepareBpmnMLModelGenerationRequest<TComponentLibrary> {

    private static final Logger LOG = LoggerFactory.getLogger(PrepareBpmnMLDetailLevelModelGenerationRequest.class);

    public PrepareBpmnMLDetailLevelModelGenerationRequest(PrepareAndSubmitMLRequestForLevelParams<?, ?, TComponentLibrary, ?, ?, ?> params, BpmnGlobalVariableLibrary globalVariableLibrary) {
        super(params, globalVariableLibrary);
    }

    @Override
    protected List<PromptSubstitution> generateAdditionalPromptSubstitutions(ModelSchema modelSchema, Context context, String request) {
        final var substitutions = new ArrayList<>(super.generateAdditionalPromptSubstitutions(modelSchema, context, request));
        final boolean isCopilotMode = getPayload().hasData(MultiLevelModelStandardPayloadData.SerializedReverseRender.toString());

        // Apply copilot scoping before prompt generation (parse impact analysis, trim, mask)
        if (isCopilotMode) {
            prepareImpactAnalysisScoping();

            // Override component library with the full unfiltered library
            substitutions.add(new PromptSubstitution(StandardPromptPlaceholders.COMPONENT_LIBRARY, getComponentLibrary().defaultSerialize()));
            
            // Provide all global variables (in normal mode, this is filtered to those used in the HL model)
            substitutions.add(new PromptSubstitution(BpmnPromptPlaceholders.GLOBAL_VARIABLES_USED_IN_HL_MODEL, getGlobalVariableLibrary().defaultSerialize()));
        }

        addPayloadSubstitutionIfPresent(substitutions, MultiLevelModelStandardPayloadData.ScopedDetailLevelModel);
        addPayloadSubstitutionIfPresent(substitutions, MultiLevelModelStandardPayloadData.ImpactAnalysisMaskingInstructions);

        return substitutions;
    }

    private void addPayloadSubstitutionIfPresent(List<PromptSubstitution> substitutions, MultiLevelModelStandardPayloadData key) {
        final String value = getPayload().getOrElse(key, (String) null);
        if (value != null && !value.isBlank()) {
            substitutions.add(new PromptSubstitution(key.toString(), value));
        }
    }

    private void prepareImpactAnalysisScoping() {
        final String existingOriginal = getPayload().getOrElse(MultiLevelModelStandardPayloadData.OriginalDetailLevelModel, (String) null);
        if (existingOriginal != null && !existingOriginal.isBlank()) {
            return;
        }

        // Resolve impact analysis: parse raw JSON from LLM if needed
        final ImpactAnalysisResult impact = resolveImpactAnalysis();
        if (impact == null) {
            return; // No impact analysis, use standard (unscoped) copilot generation
        }

        final String serializedReverseRender = getPayload().getOrElse(MultiLevelModelStandardPayloadData.SerializedReverseRender, (String) null);
        if (serializedReverseRender == null || serializedReverseRender.isBlank()) {
            LOG.debug("No serialized reverse render available, skipping copilot scoping.");
            return;
        }

        // Parse the full model
        final var parser = new IntermediateModelParser<>(BpmnIntermediateModel.class);
        final var model = parser.parse(serializedReverseRender).orElse(null);
        if (model == null) {
            LOG.warn("Failed to parse existing model, skipping copilot scoping.");
            return;
        }

        final Set<String> affectedIds = impact.allImpactedIds();
        if (affectedIds.isEmpty()) {
            LOG.info("Impact analysis found no affected nodes, skipping scoping.");
            return;
        }

        // Filter to only affected nodes + processConfig
        List<ElementNode> affectedNodes = model.getNodes().stream()
                .filter(node -> affectedIds.contains(node.getId()))
                .collect(Collectors.toList());

        LOG.info("Copilot scoping: {} total nodes, {} affected nodes for generation.",
                model.getNodes().size(), affectedNodes.size());

        BpmnIntermediateModel trimmedModel = new BpmnIntermediateModel();
        trimmedModel.setNodes(affectedNodes);
        getPayload().put(MultiLevelModelStandardPayloadData.ScopedDetailLevelModel, trimmedModel.serialize());

        // Build and store masking instructions
        getPayload().put(MultiLevelModelStandardPayloadData.ImpactAnalysisMaskingInstructions, buildTrimmedModelInstructions(impact));
    }

    private ImpactAnalysisResult resolveImpactAnalysis() {
        final Object impactAnalysisContent = getPayload().getOrElse(MultiLevelModelStandardPayloadData.ImpactAnalysis, (Object) null);
        if (impactAnalysisContent == null) return null;

        if (impactAnalysisContent instanceof ImpactAnalysisResult result) {
            return result;
        }

        try {
            ImpactAnalysisResult result = ImpactAnalysisResult.fromJson(impactAnalysisContent.toString());
            LOG.info("Impact analysis parsed: affected={}, addNodes={}, remove={}, reasoning='{}'",
                    result.getAffectedNodeIds(), result.isAddNodes(), result.getRemoveNodeIds(),
                    result.getReasoning());
            getPayload().put(MultiLevelModelStandardPayloadData.ImpactAnalysis, result);
            return result;
        } catch (Exception e) {
            LOG.warn("Failed to parse impact analysis response, proceeding without scoping: {}", e.getMessage());
            getPayload().remove(MultiLevelModelStandardPayloadData.ImpactAnalysis);
            return null;
        }
    }

    private static String buildTrimmedModelInstructions(ImpactAnalysisResult impact) {
        StringBuilder sb = new StringBuilder();

        if (impact.getRemoveNodeIds() != null && !impact.getRemoveNodeIds().isEmpty()) {
            sb.append("Nodes to remove: ").append(impact.getRemoveNodeIds())
              .append("\nThese nodes must be deleted and must NOT be included in your response. ")
              .append("Update connections of their neighbors to bypass removed nodes and ensure no connections reference a removed node ID.\n");
        }
        if (impact.isAddNodes()) {
            sb.append("New nodes may need to be added to satisfy the request.\n");
        }
        sb.append("Reasoning: ").append(impact.getReasoning());
        return sb.toString();
    }
}
