package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.Action;
import org.rj.modelgen.ui.model.a2ui.type.CheckRule;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.ActionDeserializer;
import org.rj.modelgen.ui.model.a2ui.util.CheckableDeserializer;

import java.util.List;

public class ButtonComponent extends A2UIComponent<ButtonComponent> {
    private static final String COMPONENT_TYPE = "Button";

    String child;          // required - ComponentId reference
    String variant;        // optional - enum: primary, borderless
    Action action;         // required
    List<CheckRule> checks; // optional (from Checkable)

    private ButtonComponent(String id, String child, String variant, Action action, List<CheckRule> checks) {
        super(COMPONENT_TYPE);

        setId(id);
        this.child = child;
        this.variant = variant;
        this.action = action;
        this.checks = checks;
    }

    public List<CheckRule> getChecks() {
        return checks;
    }

    public void setChecks(List<CheckRule> checks) {
        this.checks = checks;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public String getChild() {
        return child;
    }

    public void setChild(String child) {
        this.child = child;
    }

    public static ButtonComponent parse(JSONObject json) {
        String id = json.getString("id");
        String child = json.getString("child");
        String variant = json.optString("variant", null);
        Action action = ActionDeserializer.parse(json.getJSONObject("action"));
        List<CheckRule> checks = json.has("checks")
                ? CheckableDeserializer.parseChecks(json.getJSONArray("checks"))
                : List.of();
        return new ButtonComponent(id, child, variant, action, checks);
    }

    @Override
    public <R, V extends A2UIComponentVisitor<R>> R accept(V visitor) {
        return visitor.visitButton(this);
    }
}
