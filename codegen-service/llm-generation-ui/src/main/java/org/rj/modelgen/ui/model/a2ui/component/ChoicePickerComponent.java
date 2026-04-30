package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONArray;
import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.CheckRule;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;
import org.rj.modelgen.ui.model.a2ui.type.DynamicStringList;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.CheckableDeserializer;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

import java.util.ArrayList;
import java.util.List;

public class ChoicePickerComponent extends A2UIComponent<ChoicePickerComponent> {
    private static final String COMPONENT_TYPE = "ChoicePicker";

    DynamicString label;           // optional
    String variant;                // optional - enum: multipleSelection, mutuallyExclusive
    List<ChoiceOption> options;    // required (may be empty if optionsBinding is set)
    DynamicStringList optionsBinding; // optional - a DataBinding or function call to resolve options dynamically
    DynamicStringList value;       // required - list of currently selected values
    String displayStyle;           // optional - enum: checkbox, chips
    Boolean filterable;            // optional - if true, shows a search input
    List<CheckRule> checks;        // optional (from Checkable)

    protected ChoicePickerComponent(String id, DynamicString label, String variant, List<ChoiceOption> options,
                                  DynamicStringList value, String displayStyle, Boolean filterable, List<CheckRule> checks) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.label = label;
        this.variant = variant;
        this.options = options;
        this.value = value;
        this.displayStyle = displayStyle;
        this.filterable = filterable;
        this.checks = checks;
    }

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

    public DynamicStringList getOptionsBinding() {
        return optionsBinding;
    }

    public void setOptionsBinding(DynamicStringList optionsBinding) {
        this.optionsBinding = optionsBinding;
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

    public static ChoicePickerComponent parse(JSONObject json) {
        String id = json.getString("id");
        DynamicString label = json.has("label")
                ? DynamicTypeDeserializer.deserializeDynamicString(json.get("label"))
                : null;
        String variant = json.optString("variant", null);

        // Options can be a literal JSONArray of ChoiceOption objects, or a
        // dynamic reference (DataBinding / function call) that resolves at runtime
        List<ChoiceOption> options = new ArrayList<>();
        DynamicStringList optionsBinding = null;
        Object rawOptions = json.get("options");
        if (rawOptions instanceof JSONArray optionsArray) {
            for (int i = 0; i < optionsArray.length(); i++) {
                options.add(ChoiceOption.parse(optionsArray.getJSONObject(i)));
            }
        } else {
            // DataBinding or function call — store as a dynamic reference
            optionsBinding = DynamicTypeDeserializer.deserializeDynamicStringList(rawOptions);
        }

        DynamicStringList value = DynamicTypeDeserializer.deserializeDynamicStringList(json.get("value"));
        String displayStyle = json.optString("displayStyle", null);
        Boolean filterable = json.has("filterable") ? json.getBoolean("filterable") : null;
        List<CheckRule> checks = json.has("checks")
                ? CheckableDeserializer.parseChecks(json.getJSONArray("checks"))
                : List.of();

        ChoicePickerComponent comp = new ChoicePickerComponent(id, label, variant, options, value, displayStyle, filterable, checks);
        comp.setOptionsBinding(optionsBinding);
        return comp;
    }

    @Override
    public <R, V extends A2UIComponentVisitor<R>> R accept(V visitor) {
        return visitor.visitChoicePicker(this);
    }
}
