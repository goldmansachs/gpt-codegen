package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

public class ChoiceOption {
    DynamicString label;   // required - display text
    String value;          // required - stable value

    private ChoiceOption(DynamicString label, String value) {
        this.label = label;
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public DynamicString getLabel() {
        return label;
    }

    public void setLabel(DynamicString label) {
        this.label = label;
    }

    public static ChoiceOption parse(JSONObject json) {
        DynamicString label = DynamicTypeDeserializer.deserializeDynamicString(json.get("label"));
        String value = json.getString("value");
        return new ChoiceOption(label, value);
    }
}
