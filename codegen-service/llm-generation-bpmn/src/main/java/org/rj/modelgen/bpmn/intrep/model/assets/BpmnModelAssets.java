package org.rj.modelgen.bpmn.intrep.model.assets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.rj.modelgen.bpmn.models.generation.validation.PayloadVariable;
import org.rj.modelgen.llm.intrep.assets.ModelAssets;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BpmnModelAssets extends ModelAssets<ElementNodeUnresolvedInput> {

    private List<BpmnUIComponent> uiComponents;
    private List<PayloadVariable> startingPayload;

    public BpmnModelAssets() {
        super();
    }

    /**
     * Collects the set of unresolved input keys for the given node from the model assets.
     * Returns an empty set if no unresolved inputs are available for the node.
     */
    @JsonIgnore
    public Set<String> collectUnresolvedInputKeys(String nodeId) {
        var inputs = getUnresolvedInputs();
        if (inputs == null || nodeId == null) return Set.of();

        Set<String> unresolvedInputKeys = new HashSet<>();
        for (var unresolved : inputs) {
            if (nodeId.equals(unresolved.getNodeId())) {
                unresolvedInputKeys.add(unresolved.getInputKey());
            }
        }
        return unresolvedInputKeys;
    }

    @Override
    public List<ElementNodeUnresolvedInput> getUnresolvedInputs() {
        return super.getUnresolvedInputs();
    }

    @Override
    public void setUnresolvedInputs(List<ElementNodeUnresolvedInput> unresolvedInputs) {
        super.setUnresolvedInputs(unresolvedInputs);
    }

    public List<BpmnUIComponent> getUiComponents() {
        return uiComponents;
    }

    public void setUiComponents(List<BpmnUIComponent> uiComponents) {
        this.uiComponents = uiComponents;
    }

    public List<PayloadVariable> getStartingPayload() {
        return startingPayload;
    }

    public void setStartingPayload(List<PayloadVariable> startingPayload) {
        this.startingPayload = startingPayload;
    }
}
