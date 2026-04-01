package org.rj.modelgen.ui.parser;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.component.*;

public class BasicCatalogA2UIComponentParser extends A2UIComponentParser {

    @Override
    public A2UIComponent<?> parseComponent(JSONObject json) {
        String type = json.getString("component");

        return switch (type) {
            case "Text"          -> TextComponent.parse(json);
            case "Image"         -> ImageComponent.parse(json);
            case "Icon"          -> IconComponent.parse(json);
            case "Video"         -> VideoComponent.parse(json);
            case "AudioPlayer"   -> AudioPlayerComponent.parse(json);
            case "Row"           -> RowComponent.parse(json);
            case "Column"        -> ColumnComponent.parse(json);
            case "List"          -> ListComponent.parse(json);
            case "Tabs"          -> TabsComponent.parse(json);
            case "Button"        -> ButtonComponent.parse(json);
            case "TextField"     -> TextFieldComponent.parse(json);
            case "CheckBox"      -> CheckBoxComponent.parse(json);
            case "ChoicePicker"  -> ChoicePickerComponent.parse(json);
            case "Slider"        -> SliderComponent.parse(json);
            case "DateTimeInput" -> DateTimeInputComponent.parse(json);
            case "Card"          -> CardComponent.parse(json);
            case "Divider"       -> DividerComponent.parse(json);
            case "Modal"         -> ModalComponent.parse(json);
            default -> throw new IllegalArgumentException("Unknown component type: " + type);
        };
    }
}
