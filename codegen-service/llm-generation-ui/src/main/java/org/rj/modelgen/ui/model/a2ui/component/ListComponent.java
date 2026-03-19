package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.ChildList;

public class ListComponent extends A2UIComponent<ListComponent> {
    ChildList children;    // required - array of ComponentId strings or a template object
    String direction;      // optional - enum: vertical, horizontal
    String align;          // optional - enum: start, center, end, stretch

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
}