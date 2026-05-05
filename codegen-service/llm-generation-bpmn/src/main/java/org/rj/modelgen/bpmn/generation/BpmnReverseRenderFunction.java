package org.rj.modelgen.bpmn.generation;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.BpmnReverseRenderer;
import org.rj.modelgen.bpmn.models.generation.multilevel.BpmnMultiLevelGenerationModel;
import org.rj.modelgen.llm.models.generation.multilevel.states.ReverseRenderFunction;
import org.rj.modelgen.llm.state.ModelInterfaceStateMachine;
import org.rj.modelgen.llm.util.Result;

public class BpmnReverseRenderFunction implements ReverseRenderFunction<BpmnModelInstance, BpmnIntermediateModel> {

    BpmnGlobalVariableLibrary globalVariableLibrary;

    public BpmnReverseRenderFunction() {
        this.globalVariableLibrary = BpmnGlobalVariableLibrary.defaultLibrary();
    }

    public BpmnReverseRenderFunction(BpmnGlobalVariableLibrary globalVariableLibrary) {
        this.globalVariableLibrary = globalVariableLibrary;
    }

    @Override
    public Result<BpmnIntermediateModel, String> reverseRenderModelToIR(BpmnModelInstance model, ModelInterfaceStateMachine executionModel) {
        try {
            final BpmnComponentLibrary resolvedLibrary = resolveComponentLibrary(executionModel);
            final var reverseRenderer = new BpmnReverseRenderer(model, resolvedLibrary, globalVariableLibrary);
            final BpmnIntermediateModel rendered = reverseRenderer.generateBpmnIntermediateModel();

            return Result.Ok(rendered);
        }
        catch (Throwable t) {
            return Result.Err("Could not reverse render intermediate model from input BPMN model: " + t.getMessage());
        }
    }

    private BpmnComponentLibrary resolveComponentLibrary(ModelInterfaceStateMachine executionModel) {
        if (executionModel instanceof BpmnMultiLevelGenerationModel bpmnModel) {
            return bpmnModel.getComponentLibrary();
        }
        throw new IllegalStateException("No component library available for reverse rendering");
    }

}