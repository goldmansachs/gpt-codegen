package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.models.generation.multilevel.data.ImpactAnalysisResult;
import org.rj.modelgen.llm.component.ComponentLibrary;
import org.rj.modelgen.llm.context.provider.ContextProvider;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.prompt.TemplatedPromptGenerator;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.rj.modelgen.llm.statemodel.states.common.PrepareAndSubmitLlmGenericRequest;
import org.rj.modelgen.llm.util.StringSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class InitializeBpmnPayload<TPromptGenerator extends TemplatedPromptGenerator<TPromptGenerator>, TComponentLibrary extends ComponentLibrary<?>>
        extends PrepareAndSubmitLlmGenericRequest<TPromptGenerator, TComponentLibrary> {

    private static final Logger LOG = LoggerFactory.getLogger(InitializeBpmnPayload.class);

    public InitializeBpmnPayload(ContextProvider contextProvider, TPromptGenerator promptGenerator,
                                 StringSerializable promptType, TComponentLibrary componentLibrary) {
        super(contextProvider, promptGenerator, promptType, componentLibrary);
    }

    @Override
    public String getDescription() {
        return "Generating starting payload variables";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal inputSignal) {
        boolean skipPayloadGeneration = shouldSkipPayloadGeneration();
        if (skipPayloadGeneration) {
            LOG.info("Impact analysis indicates no payload change required, skipping payload generation");
            return outboundSignal(StandardSignals.SKIPPED, "Payload generation skipped - no payload change required").mono();
        }

        return super.invokeAction(inputSignal);
    }

    private boolean shouldSkipPayloadGeneration() {
        final Object raw = getPayload().getOrElse(MultiLevelModelStandardPayloadData.ImpactAnalysis, (Object) null);
        if (raw == null) return false; // Forward pass - no impact analysis detected

        ImpactAnalysisResult result;
        if (raw instanceof ImpactAnalysisResult ia) {
            result = ia;
        } else {
            try {
                result = ImpactAnalysisResult.fromJson(raw.toString());
                getPayload().put(MultiLevelModelStandardPayloadData.ImpactAnalysis, result);
            } catch (Exception e) {
                LOG.warn("Failed to parse impact analysis result, running payload generation: {}", e.getMessage());
                return false;
            }
        }

        return !result.isPayloadChangeRequired();
    }
}
