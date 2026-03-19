package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.ChildList;

public class ColumnComponent extends A2UIComponent<ColumnComponent> {
    ChildList children;    // required - array of ComponentId strings or a template object
    String justify;        // optional - enum: start, center, end, spaceBetween, spaceAround, spaceEvenly, stretch
    String align;          // optional - enum: center, end, start, stretch

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
}