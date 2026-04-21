package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;

public class DividerComponent extends A2UIComponent<DividerComponent> {
    private static final String COMPONENT_TYPE = "Divider";

    String axis;           // optional - enum: horizontal, vertical (default: horizontal)

    protected DividerComponent(String id, String axis) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.axis = axis;
    }

    public String getAxis() {
        return axis;
    }

    public void setAxis(String axis) {
        this.axis = axis;
    }

    public static DividerComponent parse(JSONObject json) {
        String id = json.getString("id");
        String axis = json.optString("axis", null);
        return new DividerComponent(id, axis);
    }

    @Override
    public <R, V extends A2UIComponentVisitor<R>> R accept(V visitor) {
        return visitor.visitDivider(this);
    }
}