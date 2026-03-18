package org.rj.modelgen.bpmn.intrep.model.assets;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;

public class BpmnUIComponent {

    private String id;

    @JsonRawValue
    @JsonDeserialize(using = RawJsonDeserializer.class)
    private String content;

    public BpmnUIComponent() {
    }

    public BpmnUIComponent(String id, String content) {
        this.id = id;
        this.content = content;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Deserializes a JSON object/value back into its raw string representation,
     * paired with @JsonRawValue which serializes the string as raw JSON.
     */
    static class RawJsonDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return p.readValueAsTree().toString();
        }
    }
}
