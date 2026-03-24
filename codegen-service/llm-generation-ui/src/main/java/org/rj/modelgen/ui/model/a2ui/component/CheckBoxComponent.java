package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.CheckRule;
import org.rj.modelgen.ui.model.a2ui.type.DynamicBoolean;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.CheckableDeserializer;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

import java.util.List;

public class CheckBoxComponent extends A2UIComponent<CheckBoxComponent> {
    private static final String COMPONENT_TYPE = "CheckBox";

    DynamicString label;       // required
    DynamicBoolean value;      // required
    List<CheckRule> checks;    // optional (from Checkable)

    private CheckBoxComponent(String id, DynamicString label, DynamicBoolean value, List<CheckRule> checks) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.label = label;
        this.value = value;
        this.checks = checks;
    }

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

    public static CheckBoxComponent parse(JSONObject json) {
        String id = json.getString("id");
        DynamicString label = DynamicTypeDeserializer.deserializeDynamicString(json.get("label"));
        DynamicBoolean value = DynamicTypeDeserializer.deserializeDynamicBoolean(json.get("value"));
        List<CheckRule> checks = json.has("checks")
                ? CheckableDeserializer.parseChecks(json.getJSONArray("checks"))
                : List.of();
        return new CheckBoxComponent(id, label, value, checks);
    }

    @Override
    public <T> T accept(A2UIComponentVisitor<T> visitor) {
        return visitor.visitCheckBox(this);
    }
}