package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.CheckRule;
import org.rj.modelgen.ui.model.a2ui.type.DynamicBoolean;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;

import java.util.List;

public class CheckBoxComponent extends A2UIComponent<CheckBoxComponent> {
    DynamicString label;       // required
    DynamicBoolean value;      // required
    List<CheckRule> checks;    // optional (from Checkable)

    public DynamicBoolean getValue() {
        return value;
    }

    public void setValue(DynamicBoolean value) {
        this.value = value;
    }

    public DynamicString getLabel() {
        return label;
    }

    public void setLabel(DynamicString label) {
        this.label = label;
    }

    public List<CheckRule> getChecks() {
        return checks;
    }

    public void setChecks(List<CheckRule> checks) {
        this.checks = checks;
    }
}