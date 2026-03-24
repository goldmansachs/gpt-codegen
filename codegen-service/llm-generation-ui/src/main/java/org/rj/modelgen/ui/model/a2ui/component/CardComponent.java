package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;

public class CardComponent extends A2UIComponent<CardComponent> {
    private static final String COMPONENT_TYPE = "Card";

    String child;          // required - ComponentId reference to a single child

    protected CardComponent(String id, String child) {
        super(COMPONENT_TYPE);

        setId(id);
        this.child = child;
    }

    public String getChild() {
        return child;
    }

    public void setChild(String child) {
        this.child = child;
    }

    public static CardComponent parse(JSONObject json) {
        String id = json.getString("id");
        String child = json.getString("child");

        return new CardComponent(id, child);
    }

    @Override
    public <R> R accept(A2UIComponentVisitor<R> visitor) {
        return visitor.visitCard(this);
    }
}