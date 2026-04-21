package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.ChildList;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

public class ListComponent extends A2UIComponent<ListComponent> {
    private static final String COMPONENT_TYPE = "List";

    ChildList children;    // required - array of ComponentId strings or a template object
    String direction;      // optional - enum: vertical, horizontal
    String align;          // optional - enum: start, center, end, stretch

    protected ListComponent(String id, ChildList children, String direction, String align) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.children = children;
        this.direction = direction;
        this.align = align;
    }

    public String getAlign() {
        return align;
    }

    public void setAlign(String align) {
        this.align = align;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public ChildList getChildren() {
        return children;
    }

    public void setChildren(ChildList children) {
        this.children = children;
    }

    public static ListComponent parse(JSONObject json) {
        String id = json.getString("id");
        ChildList children = DynamicTypeDeserializer.deserializeChildList(json.get("children"));
        String direction = json.optString("direction", null);
        String align = json.optString("align", null);
        return new ListComponent(id, children, direction, align);
    }

    @Override
    public <R, V extends A2UIComponentVisitor<R>> R accept(V visitor) {
        return visitor.visitList(this);
    }
}