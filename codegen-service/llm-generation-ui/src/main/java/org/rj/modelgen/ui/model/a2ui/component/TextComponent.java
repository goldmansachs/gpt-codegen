package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

public class TextComponent extends A2UIComponent<TextComponent> {
    private static final String COMPONENT_TYPE = "Text";

    DynamicString text;    // required - supports simple Markdown
    String variant;        // optional - enum: h1, h2, h3, h4, h5, caption, body

    private TextComponent(String id, DynamicString text, String variant) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.text = text;
        this.variant = variant;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public DynamicString getText() {
        return text;
    }

    public void setText(DynamicString text) {
        this.text = text;
    }

    public static TextComponent parse(JSONObject json) {
        String id = json.getString("id");
        DynamicString text = DynamicTypeDeserializer.deserializeDynamicString(json.get("text"));
        String variant = json.optString("variant", null);
        return new TextComponent(id, text, variant);
    }

    @Override
    public <T> T accept(A2UIComponentVisitor<T> visitor) {
        return visitor.visitText(this);
    }
}