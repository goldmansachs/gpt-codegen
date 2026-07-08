package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.models.generation.base.signals.BpmnGenerationSignals;
import org.rj.modelgen.bpmn.models.generation.multilevel.data.ImpactAnalysisResult;
import org.rj.modelgen.llm.context.provider.ContextProvider;
import org.rj.modelgen.llm.models.generation.multilevel.prompt.MultiLevelGenerationModelPromptGenerator;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.state.ModelInterfacePayload;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.rj.modelgen.llm.statemodel.states.common.PrepareAndSubmitLlmGenericRequest;
import org.rj.modelgen.llm.util.StringSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class BpmnSanitizingPrePass extends PrepareAndSubmitLlmGenericRequest<MultiLevelGenerationModelPromptGenerator, BpmnComponentLibrary> {

    private static final Logger LOG = LoggerFactory.getLogger(BpmnSanitizingPrePass.class);
    private static final String BEGIN_MARKER = "(BEGIN)";

    public BpmnSanitizingPrePass(ContextProvider contextProvider, MultiLevelGenerationModelPromptGenerator promptGenerator,
                                  StringSerializable promptType, BpmnComponentLibrary componentLibrary) {
        super(contextProvider, promptGenerator, promptType, componentLibrary);
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal inputSignal) {
        return super.invokeAction(inputSignal).map(result -> {
            if (StandardSignals.SUCCESS.toString().equals(result.getId()) || StandardSignals.SKIPPED.toString().equals(result.getId())) {
                final ModelInterfacePayload resultPayload = result.getPayload();
                applyBpmnSanitizationPostProcessing(resultPayload);
                final String routingSignal = resolveRoutingSignal(resultPayload);
                return outboundSignal(routingSignal, result.getDescription())
                        .withPayloadData(resultPayload);
            }
            return result;
        });
    }

    // If the LLM adds commentary before the (BEGIN) marker, strip it so GenerateSubproblems only sees the structured block it expects to decompose.
    private void applyBpmnSanitizationPostProcessing(ModelInterfacePayload payload) {
        final String request = payload.getOrElse(StandardModelData.Request, (String) null);
        if (request == null || request.isEmpty()) return;

        payload.put(StandardModelData.Request, extractLastBeginEndBlock(request));
    }

    private static String extractLastBeginEndBlock(String sanitizedOutput) {
        if (sanitizedOutput == null || sanitizedOutput.isEmpty()) {
            return sanitizedOutput;
        }

        final int lastBeginIdx = sanitizedOutput.toLowerCase().lastIndexOf(BEGIN_MARKER.toLowerCase());
        if (lastBeginIdx < 0) {
            return sanitizedOutput;
        }

        return sanitizedOutput.substring(lastBeginIdx);
    }

    private String resolveRoutingSignal(ModelInterfacePayload payload) {
        final String canvasModel = payload.getOrElse(StandardModelData.CanvasModel, (String) null);
        final boolean isCopilotMode = canvasModel != null && !canvasModel.isBlank();

        if (!isCopilotMode) {
            return BpmnGenerationSignals.InitialGenerationRequired.toString();
        }

        final ImpactAnalysisResult impact = resolveImpactAnalysis(payload);
        if (impact != null && impact.isPayloadChangeRequired()) {
            return StandardSignals.SUCCESS.toString();
        }
        return StandardSignals.SKIPPED.toString();
    }

    private ImpactAnalysisResult resolveImpactAnalysis(ModelInterfacePayload payload) {
        final Object raw = payload.getOrElse(MultiLevelModelStandardPayloadData.ImpactAnalysis, (Object) null);
        if (raw == null) return null;

        if (raw instanceof ImpactAnalysisResult result) return result;

        try {
            return ImpactAnalysisResult.fromJson(raw.toString());
        } catch (Exception e) {
            LOG.warn("Failed to parse impact analysis result during sanitization routing: {}", e.getMessage());
            return null;
        }
    }
}
