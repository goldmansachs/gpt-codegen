package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;

public class DividerComponent extends A2UIComponent<DividerComponent> {
    String axis;           // optional - enum: horizontal, vertical (default: horizontal)

    public String getAxis() {
        return axis;
    }

    public void setAxis(String axis) {
        this.axis = axis;
    }
}