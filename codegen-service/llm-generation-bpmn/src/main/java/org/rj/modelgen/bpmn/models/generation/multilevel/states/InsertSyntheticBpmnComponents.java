package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.models.generation.base.signals.BpmnGenerationSignals;
import org.rj.modelgen.bpmn.models.generation.multilevel.BpmnMultiLevelGenerationModel;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceStateMachine;
import org.rj.modelgen.llm.statemodel.states.common.InsertSyntheticComponents;
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
                            // In copilot mode, emit CopilotDataInitialized to proceed directly to detail-level generation
                            final boolean isCopilotMode = getPayload().hasData(MultiLevelModelStandardPayloadData.ReverseRenderedIntermediateModel.toString());
                            final String signal = isCopilotMode
                                    ? BpmnGenerationSignals.CopilotDataInitialized.toString()
                                    : getSuccessSignalId();
                            return outboundSignal(signal).mono();
                        })
                        .orElse(this::error));
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
