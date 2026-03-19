package org.rj.modelgen.ui.model.a2ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.type.AccessibilityAttribute;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;

public abstract class A2UIComponent<T extends A2UIComponent<T>> {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    String id;
    String component;
    AccessibilityAttribute accessibility;
    Double weight;

    protected A2UIComponent(String component){
        this.component = component;
    }

    protected void setId(String id) {
        this.id = id;
    }

    protected void setComponent(String component) {
        this.component = component;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public void setAccessibility(AccessibilityAttribute accessibility) {
        this.accessibility = accessibility;
    }

    public String getId() {
        return id;
    }

    public String getComponent() {
        return component;
    }

    public AccessibilityAttribute getAccessibility() {
        return accessibility;
    }

    public Double getWeight() {
        return weight;
    }

    public abstract <R> R accept(A2UIComponentVisitor<R> visitor);
}