package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

public class ImageComponent extends A2UIComponent<ImageComponent> {
    private static final String COMPONENT_TYPE = "Image";

    DynamicString url;     // required - URL of the image
    String fit;            // optional - enum: contain, cover, fill, none, scaleDown
    String variant;        // optional - enum: icon, avatar, smallFeature, mediumFeature, largeFeature, header

    protected ImageComponent(String id, DynamicString url, String fit, String variant) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.url = url;
        this.fit = fit;
        this.variant = variant;
    }

    public String getFit() {
        return fit;
    }

    public void setFit(String fit) {
        this.fit = fit;
    }

    public DynamicString getUrl() {
        return url;
    }

    public void setUrl(DynamicString url) {
        this.url = url;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public static ImageComponent parse(JSONObject json) {
        String id = json.getString("id");
        DynamicString url = DynamicTypeDeserializer.deserializeDynamicString(json.get("url"));
        String fit = json.optString("fit", null);
        String variant = json.optString("variant", null);
        return new ImageComponent(id, url, fit, variant);
    }

    @Override
    public <R, V extends A2UIComponentVisitor<R>> R accept(V visitor) {
        return visitor.visitImage(this);
    }
}
