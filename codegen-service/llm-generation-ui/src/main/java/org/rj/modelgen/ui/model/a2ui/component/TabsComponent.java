package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONArray;
import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;

import java.util.ArrayList;
import java.util.List;

public class TabsComponent extends A2UIComponent<TabsComponent> {
    private static final String COMPONENT_TYPE = "Tabs";

    List<TabItem> tabs;    // required - array of tab definitions

    protected TabsComponent(String id, List<TabItem> tabs) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.tabs = tabs;
    }

    public List<TabItem> getTabs() {
        return tabs;
    }

    public void setTabs(List<TabItem> tabs) {
        this.tabs = tabs;
    }

    public static TabsComponent parse(JSONObject json) {
        String id = json.getString("id");
        JSONArray tabsArray = json.getJSONArray("tabs");
        List<TabItem> tabs = new ArrayList<>(tabsArray.length());
        for (int i = 0; i < tabsArray.length(); i++) {
            tabs.add(TabItem.parse(tabsArray.getJSONObject(i)));
        }
        return new TabsComponent(id, tabs);
    }

    @Override
    public <R, V extends A2UIComponentVisitor<R>> R accept(V visitor) {
        return visitor.visitTabs(this);
    }
}
