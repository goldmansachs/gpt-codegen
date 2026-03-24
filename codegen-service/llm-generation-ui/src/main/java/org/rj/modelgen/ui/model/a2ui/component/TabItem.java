package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

public class TabItem {
    DynamicString title;   // required - the tab title
    String child;          // required - ComponentId reference

    private TabItem(DynamicString title, String child) {
        this.title = title;
        this.child = child;
    }

    public String getChild() {
        return child;
    }

    public void setChild(String child) {
        this.child = child;
    }

    public DynamicString getTitle() {
        return title;
    }

    public void setTitle(DynamicString title) {
        this.title = title;
    }

    public static TabItem parse(JSONObject json) {
        DynamicString title = DynamicTypeDeserializer.deserializeDynamicString(json.get("title"));
        String child = json.getString("child");
        return new TabItem(title, child);
    }
}
