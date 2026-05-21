package org.rj.modelgen.bpmn.intrep.model.assets;

import org.rj.modelgen.bpmn.models.generation.validation.PayloadVariable;
import org.rj.modelgen.llm.intrep.assets.ModelAssets;

import java.util.List;

public class BpmnModelAssets extends ModelAssets<ElementNodeUnresolvedInput> {

    private List<BpmnUIComponent> uiComponents;
    private List<PayloadVariable> startingPayload;

    public BpmnModelAssets() {
        super();
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
