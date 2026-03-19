package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;

public class ImageComponent extends A2UIComponent<ImageComponent> {
    DynamicString url;     // required - URL of the image
    String fit;            // optional - enum: contain, cover, fill, none, scaleDown
    String variant;        // optional - enum: icon, avatar, smallFeature, mediumFeature, largeFeature, header

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
}
