package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.CheckRule;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;

import java.util.List;

public class TextFieldComponent extends A2UIComponent<TextFieldComponent> {
    DynamicString label;       // required
    DynamicString value;       // optional
    String variant;            // optional - enum: longText, number, shortText, obscured
    String validationRegexp;   // optional - regex for client-side validation
    List<CheckRule> checks;    // optional (from Checkable)

    public List<CheckRule> getChecks() {
        return checks;
    }

    public void setChecks(List<CheckRule> checks) {
        this.checks = checks;
    }

    public String getValidationRegexp() {
        return validationRegexp;
    }

    public void setValidationRegexp(String validationRegexp) {
        this.validationRegexp = validationRegexp;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public DynamicString getValue() {
        return value;
    }

    public void setValue(DynamicString value) {
        this.value = value;
    }

    public DynamicString getLabel() {
        return label;
    }

    public void setLabel(DynamicString label) {
        this.label = label;
    }
}
