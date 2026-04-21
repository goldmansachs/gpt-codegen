package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.CheckRule;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.CheckableDeserializer;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

import java.util.List;

public class TextFieldComponent extends A2UIComponent<TextFieldComponent> {
    private static final String COMPONENT_TYPE = "TextField";

    DynamicString label;       // required
    DynamicString value;       // optional
    String variant;            // optional - enum: longText, number, shortText, obscured
    String validationRegexp;   // optional - regex for client-side validation
    List<CheckRule> checks;    // optional (from Checkable)

    protected TextFieldComponent(String id, DynamicString label, DynamicString value, String variant, String validationRegexp, List<CheckRule> checks) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.label = label;
        this.value = value;
        this.variant = variant;
        this.validationRegexp = validationRegexp;
        this.checks = checks;
    }

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

    public static TextFieldComponent parse(JSONObject json) {
        String id = json.getString("id");
        DynamicString label = DynamicTypeDeserializer.deserializeDynamicString(json.get("label"));
        DynamicString value = json.has("value")
                ? DynamicTypeDeserializer.deserializeDynamicString(json.get("value"))
                : null;
        String variant = json.optString("variant", null);
        String validationRegexp = json.optString("validationRegexp", null);
        List<CheckRule> checks = json.has("checks")
                ? CheckableDeserializer.parseChecks(json.getJSONArray("checks"))
                : List.of();
        return new TextFieldComponent(id, label, value, variant, validationRegexp, checks);
    }

    @Override
    public <R, V extends A2UIComponentVisitor<R>> R accept(V visitor) {
        return visitor.visitTextField(this);
    }
}
