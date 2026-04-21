package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

public class VideoComponent extends A2UIComponent<VideoComponent> {
    private static final String COMPONENT_TYPE = "Video";

    DynamicString url;     // required - URL of the video

    protected VideoComponent(String id, DynamicString url) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.url = url;
    }

    public DynamicString getUrl() {
        return url;
    }

    public void setUrl(DynamicString url) {
        this.url = url;
    }

    public static VideoComponent parse(JSONObject json) {
        String id = json.getString("id");
        DynamicString url = DynamicTypeDeserializer.deserializeDynamicString(json.get("url"));
        return new VideoComponent(id, url);
    }

    @Override
    public <R, V extends A2UIComponentVisitor<R>> R accept(V visitor) {
        return visitor.visitVideo(this);
    }
}
