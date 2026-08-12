package org.rj.modelgen.ui.models.generation.states;

import org.rj.modelgen.llm.schema.ModelSchema;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.statemodel.signals.common.CommonStateInterface;
import org.rj.modelgen.ui.models.generation.data.UIGenerationModelInputPayload;
import org.rj.modelgen.ui.models.generation.data.UIImpactAnalysisResult;
import org.rj.modelgen.ui.models.generation.schema.UIGenerationImpactAnalysisSchema;
import org.rj.modelgen.ui.models.generation.signals.UIGenerationSignals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Evaluates the impact analysis LLM response and routes the pipeline accordingly:
 * <ul>
 *   <li>No changes required - short-circuit to Complete with the LLM's reasoning as
 *       the user-facing reply. In copilot mode the existing canvas is passed through
 *       untouched.</li>
 *   <li>Copilot mode with changes required - route to the target-specific generation
 *       flow (impact analysis result, formal analysis and commentary are all available
 *       to downstream states via the payload).</li>
 *   <li>Initial generation with changes required - route to the target-specific
 *       generation flow using the formal analysis as the source-of-truth description.</li>
 * </ul>
 */
public class EvaluateUIImpactAnalysis extends ModelInterfaceState implements CommonStateInterface {

    private static final Logger LOG = LoggerFactory.getLogger(EvaluateUIImpactAnalysis.class);
    private static final ModelSchema SCHEMA = new UIGenerationImpactAnalysisSchema();

    private static final String DEFAULT_NO_CHANGE_COMMENTARY = "It looks like no changes are needed to satisfy this request.";
    private static final String DEFAULT_NO_GENERATION_COMMENTARY = "It looks like your request isn't asking me to build a UI. Could you tell me more about the form you'd like to create?";

    public EvaluateUIImpactAnalysis() {
        super(EvaluateUIImpactAnalysis.class);
    }

    @Override
    public String getDescription() {
        return "Evaluate UI impact analysis to determine whether generation should proceed";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal inputSignal) {
        final String canvasModel = getPayload().getOrElse(StandardModelData.CanvasModel, (String) null);
        final boolean isCopilotMode = canvasModel != null && !canvasModel.isBlank();
        final UIImpactAnalysisResult impact = resolveImpactAnalysis();

        if (impact == null) {
            LOG.debug("No impact analysis present - continuing with standard {} UI generation", isCopilotMode ? "copilot" : "initial-generation");
            return isCopilotMode
                    ? outboundSignal(UIGenerationSignals.CopilotChangesRequired, "Impact analysis unavailable - continuing without formalising intent").mono()
                    : outboundSignal(UIGenerationSignals.InitialGenerationRequired, "Impact analysis unavailable - continuing with full pipeline").mono();
        }

        if (impact.isNoChangeRequired()) {
            return isCopilotMode ? shortCircuitWithExistingUI(impact) : shortCircuitInitialGeneration(impact);
        }

        if (isCopilotMode) {
            LOG.info("Copilot UI impact analysis: changes required (affected={}, add={}, remove={})",
                    impact.getAffectedComponentIds(), impact.isAddComponents(), impact.getRemoveComponentIds());
            return outboundSignal(UIGenerationSignals.CopilotChangesRequired, "Copilot changes required - continuing to target flow").mono();
        }

        LOG.info("Initial-generation UI impact analysis: generation required (add={})", impact.isAddComponents());
        return outboundSignal(UIGenerationSignals.InitialGenerationRequired, "Initial-generation request detected - continuing with full pipeline").mono();
    }

    private Mono<ModelInterfaceSignal> shortCircuitWithExistingUI(UIImpactAnalysisResult impact) {
        final String canvasModel = getPayload().getOrElse(StandardModelData.CanvasModel, (String) null);
        final String llmCommentary = impact.getCommentary();
        final String commentary = llmCommentary != null && !llmCommentary.isBlank() ? llmCommentary : DEFAULT_NO_CHANGE_COMMENTARY;

        LOG.info("Copilot UI impact analysis identified no changes required. Commentary: {}", commentary);

        return outboundSignal(UIGenerationSignals.NoChangesRequired, "No UI changes required - returning existing UI")
                .withPayloadData(UIGenerationModelInputPayload.COMMENTARY, commentary)
                .withPayloadData(UIGenerationModelInputPayload.UI_OUTPUT, canvasModel)
                .withPayloadData(StandardModelData.ModelValidationMessages, List.<String>of())
                .mono();
    }

    private Mono<ModelInterfaceSignal> shortCircuitInitialGeneration(UIImpactAnalysisResult impact) {
        final String llmCommentary = impact.getCommentary();
        final String commentary = llmCommentary != null && !llmCommentary.isBlank() ? llmCommentary : DEFAULT_NO_GENERATION_COMMENTARY;

        LOG.info("Initial-generation UI impact analysis identified no generation required. Commentary: {}", commentary);

        return outboundSignal(UIGenerationSignals.NoChangesRequired, "No UI generation required - returning empty UI")
                .withPayloadData(UIGenerationModelInputPayload.COMMENTARY, commentary)
                .withPayloadData(UIGenerationModelInputPayload.UI_OUTPUT, (String) null)
                .withPayloadData(StandardModelData.ModelValidationMessages, List.<String>of())
                .mono();
    }

    private UIImpactAnalysisResult resolveImpactAnalysis() {
        final Object raw = getPayload().getOrElse(UIGenerationModelInputPayload.IMPACT_ANALYSIS, (Object) null);
        if (raw == null) return null;

        if (raw instanceof UIImpactAnalysisResult result) {
            return result;
        }

        try {
            final String json = UIImpactAnalysisResult.sanitize(raw.toString());
            final var validation = SCHEMA.validate(json);
            if (!validation.isValid()) {
                LOG.warn("UI impact analysis response does not comply with schema ({}); falling back to normal generation", validation.getValidationErrors());
                getPayload().remove(UIGenerationModelInputPayload.IMPACT_ANALYSIS);
                return null;
            }

            final UIImpactAnalysisResult result = UIImpactAnalysisResult.fromJson(json);
            getPayload().put(UIGenerationModelInputPayload.IMPACT_ANALYSIS, result);
            getPayload().put(UIGenerationModelInputPayload.FORMAL_ANALYSIS, result.getFormalAnalysis());
            getPayload().put(UIGenerationModelInputPayload.COMMENTARY, result.getCommentary());
            return result;
        } catch (Exception e) {
            LOG.warn("Failed to parse UI impact analysis JSON ({}); raw content: [{}]; falling back to normal generation",
                    e.getMessage(), raw);
            getPayload().remove(UIGenerationModelInputPayload.IMPACT_ANALYSIS);
            return null;
        }
    }

    /**
     * Convenience helper for downstream states that want to consult the parsed impact
     * analysis result without re-doing the schema/JSON handling.
     */
    public static UIImpactAnalysisResult getImpactAnalysis(org.rj.modelgen.llm.state.ModelInterfacePayload payload) {
        final Object raw = payload.getOrElse(UIGenerationModelInputPayload.IMPACT_ANALYSIS, (Object) null);
        return raw instanceof UIImpactAnalysisResult r ? r : null;
    }
}



