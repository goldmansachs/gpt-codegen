package org.rj.modelgen.ui.model.a2ui.component;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;

public class VideoComponent extends A2UIComponent<VideoComponent> {
    DynamicString url;     // required - URL of the video

    public DynamicString getUrl() {
        return url;
    }

    public void setUrl(DynamicString url) {
        this.url = url;
    }
}
