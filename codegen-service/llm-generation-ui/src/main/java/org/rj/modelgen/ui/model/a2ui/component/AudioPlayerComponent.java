package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

public class AudioPlayerComponent extends A2UIComponent<AudioPlayerComponent> {
    private static final String COMPONENT_TYPE = "AudioPlayer";

    DynamicString url;         // required - URL of the audio
    DynamicString description; // optional - title or summary of the audio

    protected AudioPlayerComponent(String id, DynamicString url, DynamicString description) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.url = url;
        this.description = description;
    }

    public DynamicString getUrl() {
        return url;
    }

    public void setUrl(DynamicString url) {
        this.url = url;
    }

    public DynamicString getDescription() {
        return description;
    }

    public void setDescription(DynamicString description) {
        this.description = description;
    }

    public static AudioPlayerComponent parse(JSONObject json) {
        String id = json.getString("id");
        DynamicString jsonUrl = DynamicTypeDeserializer.deserializeDynamicString(json.get("url"));
        DynamicString jsonDescription = json.has("description")
                ? DynamicTypeDeserializer.deserializeDynamicString(json.get("description"))
                : null;
        return new AudioPlayerComponent(id, jsonUrl, jsonDescription);
    }

    @Override
    public <R, V extends A2UIComponentVisitor<R>> R accept(V visitor) {
        return visitor.visitAudioPlayer(this);
    }
}
