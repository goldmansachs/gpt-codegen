package org.rj.modelgen.bpmn.models.generation.base.signals;

public enum BpmnGenerationSignals {
    StartBpmnGeneration,
    NoGenerationRequired,
    InitialGenerationRequired,
    PrepareLlmRequest,
    SubmitRequestToLlm,
    ValidateLlmResponse,
    IntermediateModelIsInvalid,
    IntermediateModelIsValid,
    SkipUIGeneration,
    GenerateBpmnXmlFromLlmResponse,
    ValidateBpmnXml,
    CompleteGeneration,
    CopilotDataInitialized,
    CopilotAddNodesRequired
}
