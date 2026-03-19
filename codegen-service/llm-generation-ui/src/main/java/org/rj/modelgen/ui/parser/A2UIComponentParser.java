package org.rj.modelgen.ui.parser;

import org.json.JSONObject;
import org.rj.modelgen.llm.util.Util;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.component.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class A2UIComponentParser {
    private static final Logger LOG = LoggerFactory.getLogger(A2UIComponentParser.class);

    // Maps the "component" discriminator string to the Java class
    private static final Map<String, Class<? extends A2UIComponent<?>>> COMPONENT_REGISTRY =
            Map.ofEntries(
                    Map.entry("Text", TextComponent.class),
                    Map.entry("Image", ImageComponent.class),
                    Map.entry("Icon", IconComponent.class),
                    Map.entry("Video", VideoComponent.class),
                    Map.entry("AudioPlayer", AudioPlayerComponent.class),
                    Map.entry("Row", RowComponent.class),
                    Map.entry("Column", ColumnComponent.class),
                    Map.entry("List", ListComponent.class),
                    Map.entry("Card", CardComponent.class),
                    Map.entry("Tabs", TabsComponent.class),
                    Map.entry("Modal", ModalComponent.class),
                    Map.entry("Divider", DividerComponent.class),
                    Map.entry("Button", ButtonComponent.class),
                    Map.entry("TextField", TextFieldComponent.class),
                    Map.entry("CheckBox", CheckBoxComponent.class),
                    Map.entry("ChoicePicker", ChoicePickerComponent.class),
                    Map.entry("Slider", SliderComponent.class),
                    Map.entry("DateTimeInput", DateTimeInputComponent.class)
            );

    /**
     * Parses a single component JSON object into the appropriate
     * A2UIComponent subclass using the "component" discriminator.
     */
    public Optional<A2UIComponent<?>> parseComponent(JSONObject json) {
        String type = json.getString("component");
        if (type == null) {
            return Optional.empty();
        }

        Class<? extends A2UIComponent<?>> clazz = COMPONENT_REGISTRY.get(type);
        if (clazz == null) {
            // Unknown component type - log warning and skip
            LOG.error("Unknown component type: [{}]", type);
            return Optional.empty();
        }

        try {
            A2UIComponent<?> component = Util.deserializeJsonOrThrow(json, clazz);
            return Optional.of(component);
        } catch (Exception e) {
            LOG.error("Failed to parse component: [{}] - {}", type, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parses a list of component JSON objects and returns a map
     * keyed by component ID.
     */
    public Map<String, A2UIComponent<?>> parseComponents(List<JSONObject> componentJsonList) {
        Map<String, A2UIComponent<?>> componentMap = new HashMap<>();

        for (JSONObject json : componentJsonList) {
            parseComponent(json).ifPresent(component -> {
                if (component.getId() == null || component.getId().isBlank()) {
                    LOG.error("Component missing required 'id' field, skipping.");
                } else {
                    componentMap.put(component.getId(), component);
                }
            });
        }

        return componentMap;
    }

    public static A2UIComponent<?> parseComponent(JSONObject json) {
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
