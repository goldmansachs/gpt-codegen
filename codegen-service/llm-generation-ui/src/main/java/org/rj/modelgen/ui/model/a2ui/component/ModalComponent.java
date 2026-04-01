package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;

public class ModalComponent extends A2UIComponent<ModalComponent> {
    private static final String COMPONENT_TYPE = "Modal";

    String trigger;        // required - ComponentId of the component that opens the modal
    String content;        // required - ComponentId of the component displayed inside the modal

    private ModalComponent(String id, String trigger, String content) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.trigger = trigger;
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public static ModalComponent parse(JSONObject json) {
        String id = json.getString("id");
        String trigger = json.getString("trigger");
        String content = json.getString("content");
        return new ModalComponent(id, trigger, content);
    }

    @Override
    public <R, V extends A2UIComponentVisitor<R>> R accept(V visitor) {
        return visitor.visitModal(this);
    }
}