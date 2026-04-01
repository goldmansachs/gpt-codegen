package org.rj.modelgen.ui.parser;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.component.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class A2UIComponentParser {
    private static final Logger LOG = LoggerFactory.getLogger(A2UIComponentParser.class);

    /**
     * Parses a list of component JSON objects and returns a map
     * keyed by component ID.
     */
    public Map<String, A2UIComponent<?>> parseComponents(List<JSONObject> componentJsonList) {
        Map<String, A2UIComponent<?>> componentMap = new HashMap<>();

        for (JSONObject json : componentJsonList) {
            A2UIComponent<?> component = parseComponent(json);

            if (component.getId() == null || component.getId().isBlank()) {
                LOG.error("Component missing required 'id' field, skipping.");
            } else {
                componentMap.put(component.getId(), component);
            }
        }

        return componentMap;
    }

    /**
     * Parses a single component JSON object into the appropriate
     * A2UIComponent subclass using the "component" discriminator.
     */
    public abstract A2UIComponent<?> parseComponent(JSONObject json);
}
