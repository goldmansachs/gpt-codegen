package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.ChildList;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;

public class RowComponent extends A2UIComponent<RowComponent> {
    ChildList children;    // required - array of ComponentId strings or a template object
    String justify;        // optional - enum: center, end, spaceAround, spaceBetween, spaceEvenly, start, stretch
    String align;          // optional - enum: start, center, end, stretch

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

    @Override
    public <T> T accept(A2UIComponentVisitor<T> visitor) {
        return visitor.visitRow(this);
    }
}
