package org.rj.modelgen.ui.model.a2ui.component;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.type.CheckRule;
import org.rj.modelgen.ui.model.a2ui.type.DynamicNumber;
import org.rj.modelgen.ui.model.a2ui.type.DynamicString;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;
import org.rj.modelgen.ui.model.a2ui.util.CheckableDeserializer;
import org.rj.modelgen.ui.model.a2ui.util.DynamicTypeDeserializer;

import java.util.List;

public class SliderComponent extends A2UIComponent<SliderComponent> {
    private static final String COMPONENT_TYPE = "Slider";

    DynamicString label;       // optional
    double min;                // required
    double max;                // required
    DynamicNumber value;       // required
    List<CheckRule> checks;    // optional (from Checkable)

    private SliderComponent(String id, DynamicString label, double min, double max, DynamicNumber value, List<CheckRule> checks) {
        super(COMPONENT_TYPE);
        this.setId(id);
        this.label = label;
        this.min = min;
        this.max = max;
        this.value = value;
        this.checks = checks;
    }

    public DynamicNumber getValue() {
        return value;
    }

    public void setValue(DynamicNumber value) {
        this.value = value;
    }

    public DynamicString getLabel() {
        return label;
    }

    public void setLabel(DynamicString label) {
        this.label = label;
    }

    public double getMin() {
        return min;
    }

    public void setMin(double min) {
        this.min = min;
    }

    public double getMax() {
        return max;
    }

    public void setMax(double max) {
        this.max = max;
    }

    public List<CheckRule> getChecks() {
        return checks;
    }

    public void setChecks(List<CheckRule> checks) {
        this.checks = checks;
    }

    public static SliderComponent parse(JSONObject json) {
        String id = json.getString("id");
        DynamicString label = json.has("label")
                ? DynamicTypeDeserializer.deserializeDynamicString(json.get("label"))
                : null;
        double min = json.getDouble("min");
        double max = json.getDouble("max");
        DynamicNumber value = DynamicTypeDeserializer.deserializeDynamicNumber(json.get("value"));
        List<CheckRule> checks = json.has("checks")
                ? CheckableDeserializer.parseChecks(json.getJSONArray("checks"))
                : List.of();
        return new SliderComponent(id, label, min, max, value, checks);
    }

    @Override
    public <T> T accept(A2UIComponentVisitor<T> visitor) {
        return visitor.visitSlider(this);
    }
}