package org.rj.modelgen.bpmn.intrep.model.assets;

import org.rj.modelgen.llm.intrep.assets.ModelAssets;

import java.util.List;

public class BpmnModelAssets extends ModelAssets<ElementNodeUnresolvedInput> {

    private List<BpmnUIComponent> uiComponents;

    public BpmnModelAssets() {
        super();
    }

    public List<BpmnUIComponent> getUiComponents() {
        return uiComponents;
    }

    public void setUiComponents(List<BpmnUIComponent> uiComponents) {
        this.uiComponents = uiComponents;
    }
}
