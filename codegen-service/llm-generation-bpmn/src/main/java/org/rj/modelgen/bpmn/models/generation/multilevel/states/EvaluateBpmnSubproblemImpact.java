package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.json.JSONObject;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.models.generation.common.BpmnAdditionalModelStates;
import org.rj.modelgen.bpmn.models.generation.multilevel.data.ImpactAnalysisResult;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.statemodel.signals.common.CommonStateInterface;
import org.rj.modelgen.llm.statemodel.signals.common.StandardSignals;
import org.rj.modelgen.llm.subproblem.data.SubproblemDecompositionSignals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class EvaluateBpmnSubproblemImpact extends ModelInterfaceState implements CommonStateInterface {

    private static final Logger LOG = LoggerFactory.getLogger(EvaluateBpmnSubproblemImpact.class);

    public EvaluateBpmnSubproblemImpact() {
        super(EvaluateBpmnSubproblemImpact.class);
    }

    @Override
    public String getDescription() {
        return BpmnAdditionalModelStates.EvaluateSubproblemImpact.description();
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal inputSignal) {
        final String subproblemContent = getPayload().getOrElse(
                MultiLevelModelStandardPayloadData.SerializedReverseRender, (String) null);

        final boolean impacted = isSubproblemImpacted(subproblemContent);
        if (impacted) {
            return success("Subproblem contains impacted nodes. Continuing with detail-level generation");
        } else {
            getPayload().put(MultiLevelModelStandardPayloadData.DetailLevelModel, subproblemContent);
            if (getModel().isSubModel()) {
                return outboundSignal(SubproblemDecompositionSignals.SubModelNotImpacted.toString(), "Subproblem contains no impacted nodes. Skipping detail-level generation").mono();
            }
            return outboundSignal(StandardSignals.SKIPPED, "Skipping detail-level generation").mono();
        }
    }

    private boolean isSubproblemImpacted(String subproblemContent) {
        final ImpactAnalysisResult impact = resolveImpactAnalysis();

        if (impact == null || subproblemContent == null) {
            LOG.warn("Impact analysis result or subproblem content is null so treating subproblem as impacted");
            return true;
        }

        try {
            final BpmnIntermediateModel subproblem = BpmnIntermediateModel.fromJson(new JSONObject(subproblemContent));
            final Set<String> nodeIds = subproblem.getNodes().stream()
                    .map(node -> node.getId())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            final Set<String> impactedIds = impact.allImpactedIds();
            return nodeIds.stream().anyMatch(impactedIds::contains);

        } catch (Exception e) {
            LOG.warn("Failed to parse subproblem content for impact evaluation: {}", e.getMessage());
            return true;
        }
    }

    private ImpactAnalysisResult resolveImpactAnalysis() {
        final Object raw = getPayload().getOrElse(MultiLevelModelStandardPayloadData.ImpactAnalysis, (Object) null);
        if (raw == null) return null;

        if (raw instanceof ImpactAnalysisResult result) return result;

        try {
            return ImpactAnalysisResult.fromJson(raw.toString());
        } catch (Exception e) {
            LOG.warn("Failed to parse impact analysis result in subproblem evaluation: {}", e.getMessage());
            return null;
        }
    }
}
