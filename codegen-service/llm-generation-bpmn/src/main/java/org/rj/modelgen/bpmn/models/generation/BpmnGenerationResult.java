package org.rj.modelgen.bpmn.models.generation;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;
import org.rj.modelgen.bpmn.models.generation.base.states.BpmnGenerationComplete;
import org.rj.modelgen.llm.models.generation.GenerationResult;
import org.rj.modelgen.llm.state.ModelInterfaceExecutionResult;

import java.util.List;
import java.util.Optional;

public class BpmnGenerationResult extends GenerationResult<BpmnIntermediateModel, BpmnModelAssets> {

    private final BpmnModelInstance generatedBpmn;

    public static BpmnGenerationResult fromModelExecutionResult(ModelInterfaceExecutionResult result) {
        final var successResult = Optional.ofNullable(result).map(ModelInterfaceExecutionResult::getResult)
                .flatMap(state -> state.getAs(BpmnGenerationComplete.class));

        return successResult.map(res ->
            new BpmnGenerationResult(true, res.getIntermediateModel(), res.getModelAssets(), res.getGeneratedBpmn(), res.getBpmnValidationMessages(), result)
        ).orElseGet(() ->
            new BpmnGenerationResult(false, null, null, null, null, result)
        );
    }

    private BpmnGenerationResult(boolean successful, BpmnIntermediateModel intermediateModel, BpmnModelAssets intermediateModelMetadata, BpmnModelInstance generatedBpmn,
                                 List<String> bpmnValidationMessages, ModelInterfaceExecutionResult executionResults) {
        super(successful, intermediateModel, intermediateModelMetadata, bpmnValidationMessages, executionResults);
        this.generatedBpmn = generatedBpmn;
    }

    public BpmnModelInstance getGeneratedBpmn() {
        return generatedBpmn;
    }
}
