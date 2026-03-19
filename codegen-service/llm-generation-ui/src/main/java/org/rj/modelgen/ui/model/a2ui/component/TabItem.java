package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.type.DynamicString;

public class TabItem {
    DynamicString title;   // required - the tab title
    String child;          // required - ComponentId reference

    public String getChild() {
        return child;
    }

    public void setChild(String child) {
        this.child = child;
    }

    public DynamicString getTitle() {
        return title;
    }

    public void setTitle(DynamicString title) {
        this.title = title;
    }
}
