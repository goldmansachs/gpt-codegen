package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;

public class IconComponent extends A2UIComponent<IconComponent> {
    Object name;           // required - either a String enum value (e.g. "mail", "search", "home", etc.) or a DataBinding with a path

    public Object getName() {
        return name;
    }

    public void setName(Object name) {
        this.name = name;
    }
}
