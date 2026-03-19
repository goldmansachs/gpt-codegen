package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.CheckRule;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;
import org.rj.modelgen.ui.model.a2ui.type.DynamicStringList;

import java.util.List;

public class ChoicePickerComponent extends A2UIComponent<ChoicePickerComponent> {
    DynamicString label;           // optional
    String variant;                // optional - enum: multipleSelection, mutuallyExclusive
    List<ChoiceOption> options;    // required
    DynamicStringList value;       // required - list of currently selected values
    String displayStyle;           // optional - enum: checkbox, chips
    Boolean filterable;            // optional - if true, shows a search input
    List<CheckRule> checks;        // optional (from Checkable)

    public List<CheckRule> getChecks() {
        return checks;
    }

    public void setChecks(List<CheckRule> checks) {
        this.checks = checks;
    }

    public Boolean getFilterable() {
        return filterable;
    }

    public void setFilterable(Boolean filterable) {
        this.filterable = filterable;
    }

    public String getDisplayStyle() {
        return displayStyle;
    }

    public void setDisplayStyle(String displayStyle) {
        this.displayStyle = displayStyle;
    }

    public DynamicStringList getValue() {
        return value;
    }

    public void setValue(DynamicStringList value) {
        this.value = value;
    }

    public List<ChoiceOption> getOptions() {
        return options;
    }

    public void setOptions(List<ChoiceOption> options) {
        this.options = options;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public DynamicString getLabel() {
        return label;
    }

    public void setLabel(DynamicString label) {
        this.label = label;
    }
}

