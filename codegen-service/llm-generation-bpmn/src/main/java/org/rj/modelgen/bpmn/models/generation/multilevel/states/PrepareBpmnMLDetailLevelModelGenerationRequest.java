package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.models.generation.base.context.BpmnPromptPlaceholders;
import org.rj.modelgen.llm.component.ComponentLibrary;
import org.rj.modelgen.llm.context.Context;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.models.generation.multilevel.states.PrepareAndSubmitMLRequestForLevelParams;
import org.rj.modelgen.llm.prompt.PromptSubstitution;
import org.rj.modelgen.llm.prompt.StandardPromptPlaceholders;
import org.rj.modelgen.llm.schema.ModelSchema;

import java.util.ArrayList;
import java.util.List;

public class PrepareBpmnMLDetailLevelModelGenerationRequest<TComponentLibrary extends ComponentLibrary<?>>
        extends PrepareBpmnMLModelGenerationRequest<TComponentLibrary> {

    public PrepareBpmnMLDetailLevelModelGenerationRequest(PrepareAndSubmitMLRequestForLevelParams<?, ?, TComponentLibrary, ?, ?, ?> params, BpmnGlobalVariableLibrary globalVariableLibrary) {
        super(params, globalVariableLibrary);
    }

    @Override
    protected List<PromptSubstitution> generateAdditionalPromptSubstitutions(ModelSchema modelSchema, Context context, String request) {
        final boolean isCopilotMode = getPayload().hasData(MultiLevelModelStandardPayloadData.SerializedReverseRender.toString());
        if (!isCopilotMode) {
            return super.generateAdditionalPromptSubstitutions(modelSchema, context, request);
        }

        // In copilot mode, provide the full component library and all global variables
        // so the LLM can use any component or global variable when modifying the existing model
        final var substitutions = new ArrayList<>(super.generateAdditionalPromptSubstitutions(modelSchema, context, request));

        // Override component library with the full unfiltered library
        substitutions.add(new PromptSubstitution(StandardPromptPlaceholders.COMPONENT_LIBRARY, getComponentLibrary().defaultSerialize()));

        // Provide all global variables (in normal mode, this is filtered to those used in the HL model)
        substitutions.add(new PromptSubstitution(BpmnPromptPlaceholders.GLOBAL_VARIABLES_USED_IN_HL_MODEL, getGlobalVariableLibrary().defaultSerialize()));

        return substitutions;
    }
}
