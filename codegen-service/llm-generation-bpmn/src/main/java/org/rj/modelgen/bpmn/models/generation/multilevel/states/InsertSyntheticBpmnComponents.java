package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.models.generation.base.signals.BpmnGenerationSignals;
import org.rj.modelgen.bpmn.models.generation.multilevel.BpmnMultiLevelGenerationModel;
import org.rj.modelgen.bpmn.models.generation.multilevel.data.ImpactAnalysisResult;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceStateMachine;
import org.rj.modelgen.llm.statemodel.states.common.InsertSyntheticComponents;
import org.rj.modelgen.llm.subproblem.data.SubproblemDecompositionPayloadData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

public class InsertSyntheticBpmnComponents extends InsertSyntheticComponents<BpmnComponent> {

    private static final Logger LOG = LoggerFactory.getLogger(InsertSyntheticBpmnComponents.class);
    private final List<BpmnComponent> syntheticComponents;

    public InsertSyntheticBpmnComponents(String syntheticComponentResource) {
        this(BpmnComponentLibrary.fromResource(syntheticComponentResource).getComponents());
    }

    public InsertSyntheticBpmnComponents(List<BpmnComponent> syntheticComponents) {
        super(InsertSyntheticBpmnComponents.class);
        this.syntheticComponents = syntheticComponents;
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal inputSignal) {
        return executeLogic()
                .flatMap(result -> result
                        .map(__ -> {
                            final boolean isCopilotMode = getPayload().hasData(MultiLevelModelStandardPayloadData.ReverseRenderedIntermediateModel.toString());
                            if (!isCopilotMode) {
                                return outboundSignal(getSuccessSignalId()).mono();
                            }

                            // Use the full IR as a single subproblem when adding new nodes or if impact analysis res is malformed
                            if (shouldUseFullIr()) {
                                setupSingleSubproblemContext();
                                return outboundSignal(BpmnGenerationSignals.CopilotAddNodesRequired.toString()).mono();
                            }

                            return outboundSignal(BpmnGenerationSignals.CopilotDataInitialized.toString()).mono();
                        })
                        .orElse(this::error));
    }

    private boolean shouldUseFullIr() {
        final Object raw = getPayload().getOrElse(MultiLevelModelStandardPayloadData.ImpactAnalysis, (Object) null);
        if (raw == null) {
            LOG.warn("No impact analysis result available in copilot mode so using complete IR model");
            return true;
        }

        try {
            final ImpactAnalysisResult impact = raw instanceof ImpactAnalysisResult res
                    ? res
                    : ImpactAnalysisResult.fromJson(raw.toString());
            return impact.isAddNodes();
        } catch (Exception e) {
            LOG.warn("Failed to parse impact analysis result in copilot mode so using complete IR model");
            return true;
        }
    }

    private void setupSingleSubproblemContext() {
        final String fullIr = getPayload().getOrElse(
                MultiLevelModelStandardPayloadData.SerializedReverseRender, (String) null);

        getPayload().put(SubproblemDecompositionPayloadData.CurrentSubproblem, 0);
        getPayload().put(SubproblemDecompositionPayloadData.SubproblemCount, 1);

        if (fullIr != null) {
            final String requestKey = "%s-%d".formatted(SubproblemDecompositionPayloadData.SubproblemRequestContent, 0);
            getPayload().put(requestKey, fullIr);
        }

        LOG.info("addNodes required: bypassing subproblem decomposition, sending full IR to detail-level stage as single subproblem");
    }

    @Override
    protected List<BpmnComponent> getSyntheticComponents() {
        return syntheticComponents;
    }

    @Override
    protected void addComponentsToModel(ModelInterfaceStateMachine model, List<BpmnComponent> bpmnComponents) {
        final BpmnMultiLevelGenerationModel bpmnModel = model.getAs(BpmnMultiLevelGenerationModel.class);
        if (bpmnModel == null) {
            LOG.warn("InsertSyntheticBpmnComponents can only be used in an BPMN execution model");
            return;
        }

        final var library = bpmnModel.getComponentLibrary();
        if (library == null) {
            LOG.warn("BPMN model does not have a valid component library");
            return;
        }

        library.addComponents(bpmnComponents);
    }
}
