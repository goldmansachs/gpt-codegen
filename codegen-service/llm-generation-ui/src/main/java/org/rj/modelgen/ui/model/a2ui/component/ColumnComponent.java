package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.ChildList;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

public class ColumnComponent extends A2UIComponent<ColumnComponent> {
    private static final String COMPONENT_TYPE = "Column";

    ChildList children;    // required - array of ComponentId strings or a template object
    String justify;        // optional - enum: start, center, end, spaceBetween, spaceAround, spaceEvenly, stretch
    String align;          // optional - enum: center, end, start, stretch

    protected ColumnComponent(String id, ChildList children, String justify, String align) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.children = children;
        this.justify = justify;
        this.align = align;
    }

    public String getAlign() {
        return align;
    }

    public void setAlign(String align) {
        this.align = align;
    }

    public String getJustify() {
        return justify;
    }

    public void setJustify(String justify) {
        this.justify = justify;
    }

    public ChildList getChildren() {
        return children;
    }

    public void setChildren(ChildList children) {
        this.children = children;
    }

    public static ColumnComponent parse(JSONObject json) {
        String id = json.getString("id");
        ChildList children = DynamicTypeDeserializer.deserializeChildList(json.get("children"));
        String justify = json.optString("justify", null);
        String align = json.optString("align", null);
        return new ColumnComponent(id, children, justify, align);
    }

    @Override
    public <R, V extends A2UIComponentVisitor<R>> R accept(V visitor) {
        return visitor.visitColumn(this);
    }
}