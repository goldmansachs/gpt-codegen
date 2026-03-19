package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.CheckRule;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;

import java.util.List;

public class DateTimeInputComponent extends A2UIComponent<DateTimeInputComponent> {
    DynamicString value;       // required - ISO 8601 format
    Boolean enableDate;        // optional - if true, allows date selection
    Boolean enableTime;        // optional - if true, allows time selection
    DynamicString min;         // optional - minimum allowed date/time in ISO 8601
    DynamicString max;         // optional - maximum allowed date/time in ISO 8601
    DynamicString label;       // optional
    List<CheckRule> checks;    // optional (from Checkable)

    public List<CheckRule> getChecks() {
        return checks;
    }

    public void setChecks(List<CheckRule> checks) {
        this.checks = checks;
    }

    public DynamicString getLabel() {
        return label;
    }

    public void setLabel(DynamicString label) {
        this.label = label;
    }

    public DynamicString getMax() {
        return max;
    }

    public void setMax(DynamicString max) {
        this.max = max;
    }

    public DynamicString getMin() {
        return min;
    }

    public void setMin(DynamicString min) {
        this.min = min;
    }

    public Boolean getEnableTime() {
        return enableTime;
    }

    public void setEnableTime(Boolean enableTime) {
        this.enableTime = enableTime;
    }

    public Boolean getEnableDate() {
        return enableDate;
    }

    public void setEnableDate(Boolean enableDate) {
        this.enableDate = enableDate;
    }

    public DynamicString getValue() {
        return value;
    }

    public void setValue(DynamicString value) {
        this.value = value;
    }
}