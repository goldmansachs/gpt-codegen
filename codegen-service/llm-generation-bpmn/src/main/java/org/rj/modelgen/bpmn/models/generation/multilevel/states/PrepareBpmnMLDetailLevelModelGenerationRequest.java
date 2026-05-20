package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
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

public class PrepareBpmnMLDetailLevelModelGenerationRequest<TComponentLibrary extends ComponentLibrary<?>>
        extends PrepareBpmnMLModelGenerationRequest<TComponentLibrary> {

    private static final Logger LOG = LoggerFactory.getLogger(PrepareBpmnMLDetailLevelModelGenerationRequest.class);
    private static final IntermediateModelParser<BpmnIntermediateModel> PARSER = new IntermediateModelParser<>(BpmnIntermediateModel.class);

    public PrepareBpmnMLDetailLevelModelGenerationRequest(PrepareAndSubmitMLRequestForLevelParams<?, ?, TComponentLibrary, ?, ?, ?> params, BpmnGlobalVariableLibrary globalVariableLibrary) {
        super(params, globalVariableLibrary);
    }

    @Override
    protected List<PromptSubstitution> generateAdditionalPromptSubstitutions(ModelSchema modelSchema, Context context, String request) {
        final boolean isCopilotMode = getPayload().hasData(MultiLevelModelStandardPayloadData.SerializedReverseRender.toString());

        final var substitutions = new ArrayList<>(super.generateAdditionalPromptSubstitutions(modelSchema, context, request));

        if (isCopilotMode) {
            // In copilot mode, provide the full component library
            substitutions.add(new PromptSubstitution(StandardPromptPlaceholders.COMPONENT_LIBRARY, getComponentLibrary().defaultSerialize()));
        }

        stripCommentaryFromDetailLevelModel(substitutions);

        return substitutions;
    }

    private void stripCommentaryFromDetailLevelModel(List<PromptSubstitution> substitutions) {
        final String detailLevelModel = getPayload().getOrElse(MultiLevelModelStandardPayloadData.DetailLevelModel, (String) null);
        if (detailLevelModel == null || detailLevelModel.isBlank()) return;

        final var result = PARSER.parse(detailLevelModel);
        if (result.isOk()) {
            final var model = result.getValue();
            model.setCommentary(null);
            substitutions.add(new PromptSubstitution(MultiLevelModelStandardPayloadData.DetailLevelModel.toString(), model.serialize()));
        } else {
            LOG.debug("Could not strip commentary from detail-level model for retry prompt: {}", result.getError());
        }
    }
}
