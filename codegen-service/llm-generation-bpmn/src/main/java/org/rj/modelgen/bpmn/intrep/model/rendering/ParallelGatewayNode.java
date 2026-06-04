package org.rj.modelgen.bpmn.intrep.model.rendering;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.ParallelGatewayBuilder;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;

import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.GATEWAY_PARALLEL;

public class ParallelGatewayNode extends ElementNode {

    public ParallelGatewayNode() {
        super();
    }

    public ParallelGatewayNode(String id, String name) {
        super(id, name, GATEWAY_PARALLEL);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        ParallelGatewayBuilder gatewayBuilder = builder.parallelGateway(id).name(name);
        ParallelGateway gateway = gatewayBuilder.getElement();
        configureTaskMetadata(gateway, namespace);;
        return gatewayBuilder.done();
    }
}
