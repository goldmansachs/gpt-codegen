package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.CheckRule;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.CheckableDeserializer;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

import java.util.List;

public class DateTimeInputComponent extends A2UIComponent<DateTimeInputComponent> {
    private static final String COMPONENT_TYPE = "DateTimeInput";

    DynamicString value;       // required - ISO 8601 format
    Boolean enableDate;        // optional - if true, allows date selection
    Boolean enableTime;        // optional - if true, allows time selection
    DynamicString min;         // optional - minimum allowed date/time in ISO 8601
    DynamicString max;         // optional - maximum allowed date/time in ISO 8601
    DynamicString label;       // optional
    List<CheckRule> checks;    // optional (from Checkable)

    private DateTimeInputComponent(String id, DynamicString value, Boolean enableDate, Boolean enableTime,
                                   DynamicString min, DynamicString max, DynamicString label, List<CheckRule> checks) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.value = value;
        this.enableDate = enableDate;
        this.enableTime = enableTime;
        this.min = min;
        this.max = max;
        this.label = label;
        this.checks = checks;
    }

    public List<CheckRule> getChecks() {
        return checks;
    }

    public void setChecks(List<CheckRule> checks) {
        this.checks = checks;
    }

    public DynamicString getLabel() {
        return label;
    }

    public void setLabel(DynamicString label) {
        this.label = label;
    }

    public DynamicString getMax() {
        return max;
    }

    public void setMax(DynamicString max) {
        this.max = max;
    }

    public DynamicString getMin() {
        return min;
    }

    public void setMin(DynamicString min) {
        this.min = min;
    }

    public Boolean getEnableTime() {
        return enableTime;
    }

    public void setEnableTime(Boolean enableTime) {
        this.enableTime = enableTime;
    }

    public Boolean getEnableDate() {
        return enableDate;
    }

    public void setEnableDate(Boolean enableDate) {
        this.enableDate = enableDate;
    }

    public DynamicString getValue() {
        return value;
    }

    public void setValue(DynamicString value) {
        this.value = value;
    }

    public static DateTimeInputComponent parse(JSONObject json) {
        String id = json.getString("id");
        DynamicString value = DynamicTypeDeserializer.deserializeDynamicString(json.get("value"));
        Boolean enableDate = json.has("enableDate") ? json.getBoolean("enableDate") : null;
        Boolean enableTime = json.has("enableTime") ? json.getBoolean("enableTime") : null;
        DynamicString min = json.has("min")
                ? DynamicTypeDeserializer.deserializeDynamicString(json.get("min"))
                : null;
        DynamicString max = json.has("max")
                ? DynamicTypeDeserializer.deserializeDynamicString(json.get("max"))
                : null;
        DynamicString label = json.has("label")
                ? DynamicTypeDeserializer.deserializeDynamicString(json.get("label"))
                : null;
        List<CheckRule> checks = json.has("checks")
                ? CheckableDeserializer.parseChecks(json.getJSONArray("checks"))
                : List.of();
        return new DateTimeInputComponent(id, value, enableDate, enableTime, min, max, label, checks);
    }

    @Override
    public <T> T accept(A2UIComponentVisitor<T> visitor) {
        return visitor.visitDateTimeInput(this);
    }
}