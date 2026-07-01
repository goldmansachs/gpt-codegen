package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;
import org.rj.modelgen.bpmn.models.generation.base.signals.BpmnGenerationSignals;
import org.rj.modelgen.bpmn.models.generation.multilevel.data.ImpactAnalysisResult;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.statemodel.signals.common.CommonStateInterface;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

public class EvaluateImpactAnalysis extends ModelInterfaceState implements CommonStateInterface {

    private static final Logger LOG = LoggerFactory.getLogger(EvaluateImpactAnalysis.class);

    private static final String DEFAULT_NO_CHANGE_COMMENTARY = "It looks like no changes are needed to satisfy this request.";
    private static final String DEFAULT_NO_GENERATION_COMMENTARY = "It looks like your request isn't asking me to generate a new process model. Could you tell me more about the workflow you'd like to build?";

    public EvaluateImpactAnalysis() {
        super(EvaluateImpactAnalysis.class);
    }

    @Override
    public String getDescription() {
        return "Evaluate impact analysis to determine whether generation should proceed";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal inputSignal) {
        final String canvasModel = getPayload().getOrElse(StandardModelData.CanvasModel, (String) null);
        final boolean isCopilotMode =  canvasModel != null && !canvasModel.isBlank();
        final ImpactAnalysisResult impact = resolveImpactAnalysis();

        if (impact == null) {
            LOG.debug("No impact analysis present - continuing with standard {} generation", isCopilotMode ? "copilot" : "initial-generation");

            return isCopilotMode
                    ? outboundSignal(getSuccessSignalId()).mono()
                    : outboundSignal(BpmnGenerationSignals.InitialGenerationRequired, "Initial-generation continuing (impact analysis unavailable)").mono();
        }

        final boolean noStructuralChanges = impact.getAffectedNodeIds().isEmpty()
                && impact.getRemoveNodeIds().isEmpty()
                && !impact.isAddNodes();
        final boolean noPayloadChange = !impact.isPayloadChangeRequired();

        // Nothing to generate at all - short-circuit to 'Complete' with the LLM's reasoning as the user-facing reply.
        if (noStructuralChanges && noPayloadChange) {
            return isCopilotMode
                    ? shortCircuitWithExistingModel(impact)
                    : shortCircuitInitialGeneration(impact);
        }

        // From here on the model has decided generation is required.
        if (!isCopilotMode) {
            LOG.info("Initial-generation impact analysis: generation required (addNodes={}, payloadChange={}); continuing with full pipeline", impact.isAddNodes(), impact.isPayloadChangeRequired());
            return outboundSignal(BpmnGenerationSignals.InitialGenerationRequired, "Initial-generation request detected - continuing with preprocessing pipeline").mono();
        }

        // Copilot mode: structural changes only - bypass the payload-generation LLM call entirely
        if (noPayloadChange) {
            LOG.info("Copilot impact analysis: structural changes only (affected={}, addNodes={}, remove={}); skipping payload generation", impact.getAffectedNodeIds(), impact.isAddNodes(), impact.getRemoveNodeIds());
            return outboundSignal(StandardSignals.SKIPPED, "Skipping payload generation - no payload change required").mono();
        }

        // Payload change required - run the full pipeline.
        LOG.info("Copilot impact analysis: payload changes required (affected={}, addNodes={}, remove={}); continuing with full generation", impact.getAffectedNodeIds(), impact.isAddNodes(), impact.getRemoveNodeIds());
        return outboundSignal(getSuccessSignalId()).mono();
    }

    private Mono<ModelInterfaceSignal> shortCircuitWithExistingModel(ImpactAnalysisResult impact) {
        final String canvasModel = getPayload().getOrElse(StandardModelData.CanvasModel, (String) null);
        if (canvasModel == null || canvasModel.isBlank()) {
            LOG.warn("No canvas BPMN available - cannot short-circuit. Falling back to normal generation");
            return outboundSignal(getSuccessSignalId()).mono();
        }

        final var bpmnModel = getPayload().getOrElse(MultiLevelModelStandardPayloadData.Model.toString(), (BpmnModelInstance) null);
        final var reverseRenderedIRModel = getPayload().getOrElse(MultiLevelModelStandardPayloadData.ReverseRenderedIntermediateModel.toString(), (BpmnIntermediateModel) null);

        if (reverseRenderedIRModel == null || bpmnModel == null) {
            LOG.warn("Missing reverse-rendered IR ({}) or compiled BPMN model ({}) - cannot short-circuit. Falling back to normal generation", reverseRenderedIRModel == null, bpmnModel == null);
            return outboundSignal(getSuccessSignalId()).mono();
        }

        // Replace any existing commentary with the impact-analysis reasoning, so the conversational response describes what was understood about the user's question.
        final String reasoning = impact.getReasoning();
        reverseRenderedIRModel.setCommentary(reasoning != null && !reasoning.isBlank() ? reasoning : DEFAULT_NO_CHANGE_COMMENTARY);

        final BpmnModelAssets existingAssets = getPayload().getOrElse(StandardModelData.ModelAssets, (BpmnModelAssets) null);
        final BpmnModelAssets modelAssets = existingAssets != null ? existingAssets : new BpmnModelAssets();

        LOG.info("Copilot impact analysis identified no changes required. Reasoning: {}", reasoning);

        return outboundSignal(BpmnGenerationSignals.NoGenerationRequired, "No model changes required - returning existing model")
                .withPayloadData(StandardModelData.IntermediateModel, reverseRenderedIRModel)
                .withPayloadData(StandardModelData.GeneratedModel, bpmnModel)
                .withPayloadData(StandardModelData.ModelAssets, modelAssets)
                .withPayloadData(StandardModelData.ModelValidationMessages, List.of())
                .mono();
    }

    private Mono<ModelInterfaceSignal> shortCircuitInitialGeneration(ImpactAnalysisResult impact) {
        final String reasoning = impact.getReasoning();
        final String commentary = (reasoning != null && !reasoning.isBlank()) ? reasoning : DEFAULT_NO_GENERATION_COMMENTARY;

        final BpmnIntermediateModel emptyIntermediateModel = new BpmnIntermediateModel();
        emptyIntermediateModel.setCommentary(commentary);

        LOG.info("Initial-generation impact analysis identified no generation required. Reasoning: {}", reasoning);

        return outboundSignal(BpmnGenerationSignals.NoGenerationRequired, "No model generation required - returning empty model with commentary")
                .withPayloadData(StandardModelData.IntermediateModel, emptyIntermediateModel)
                .withPayloadData(StandardModelData.GeneratedModel, null)
                .withPayloadData(StandardModelData.ModelAssets, new BpmnModelAssets())
                .withPayloadData(StandardModelData.ModelValidationMessages, List.of())
                .mono();
    }

    private ImpactAnalysisResult resolveImpactAnalysis() {
        final Object raw = getPayload().getOrElse(MultiLevelModelStandardPayloadData.ImpactAnalysis, (Object) null);
        if (raw == null) return null;

        if (raw instanceof ImpactAnalysisResult result) {
            return result;
        }

        try {
            final ImpactAnalysisResult result = ImpactAnalysisResult.fromJson(raw.toString());
            getPayload().put(MultiLevelModelStandardPayloadData.ImpactAnalysis, result);
            return result;
        } catch (Exception e) {
            LOG.warn("Failed to parse impact analysis JSON in evaluation phase ({}); falling back to normal generation", e.getMessage());
            getPayload().remove(MultiLevelModelStandardPayloadData.ImpactAnalysis);
            return null;
        }
    }
}

