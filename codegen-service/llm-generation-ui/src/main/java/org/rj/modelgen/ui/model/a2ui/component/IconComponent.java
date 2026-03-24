package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

public class IconComponent extends A2UIComponent<IconComponent> {
    private static final String COMPONENT_TYPE = "Icon";

    Object name;           // required - either a String enum value (e.g. "mail", "search", "home", etc.) or a DataBinding with a path

    private IconComponent(String id, Object name) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.name = name;
    }

    public Object getName() {
        return name;
    }

    public void setName(Object name) {
        this.name = name;
    }

    public static IconComponent parse(JSONObject json) {
        String id = json.getString("id");
        Object name = DynamicTypeDeserializer.parseDynamicValue(json.get("name"));
        return new IconComponent(id, name);
    }

    @Override
    public <T> T accept(A2UIComponentVisitor<T> visitor) {
        return visitor.visitIcon(this);
    }
}
