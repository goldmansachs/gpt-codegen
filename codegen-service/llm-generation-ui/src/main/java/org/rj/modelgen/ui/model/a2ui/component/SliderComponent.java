package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.CheckRule;
import org.rj.modelgen.ui.model.a2ui.type.DynamicNumber;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;

import java.util.List;

public class SliderComponent extends A2UIComponent<SliderComponent> {
    DynamicString label;       // optional
    double min;                // required
    double max;                // required
    DynamicNumber value;       // required
    List<CheckRule> checks;    // optional (from Checkable)

    public DynamicNumber getValue() {
        return value;
    }

    public void setValue(DynamicNumber value) {
        this.value = value;
    }

    public DynamicString getLabel() {
        return label;
    }

    public void setLabel(DynamicString label) {
        this.label = label;
    }

    public double getMin() {
        return min;
    }

    public void setMin(double min) {
        this.min = min;
    }

    public double getMax() {
        return max;
    }

    public void setMax(double max) {
        this.max = max;
    }

    public List<CheckRule> getChecks() {
        return checks;
    }

    public void setChecks(List<CheckRule> checks) {
        this.checks = checks;
    }
}