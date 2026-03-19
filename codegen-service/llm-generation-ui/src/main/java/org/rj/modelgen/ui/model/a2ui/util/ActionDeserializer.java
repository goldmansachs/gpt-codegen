package org.rj.modelgen.ui.model.a2ui.util;

import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.type.Action;
import org.rj.modelgen.ui.model.a2ui.type.EventAction;
import org.rj.modelgen.ui.model.a2ui.type.FunctionCallAction;

import java.util.HashMap;
import java.util.Map;

public final class ActionDeserializer {
    private ActionDeserializer() {}

    public static Action parse(JSONObject json) {
        if (json.has("event")) {
            JSONObject eventObj = json.getJSONObject("event");
            String name = eventObj.getString("name");
            Map<String, Object> context = eventObj.has("context")
                    ? parseContext(eventObj.getJSONObject("context"))
                    : Map.of();
            return new EventAction(name, context);
        }
        if (json.has("functionCall")) {
            JSONObject fcObj = json.getJSONObject("functionCall");
            return new FunctionCallAction(
                    fcObj.getString("call"),
                    parseArgs(fcObj.optJSONObject("args")),
                    fcObj.optString("returnType", "void")
            );
        }
        throw new IllegalArgumentException("Action must have 'event' or 'functionCall': " + json);
    }

    private static Map<String, Object> parseContext(JSONObject contextObj) {
        Map<String, Object> context = new HashMap<>();
        for (String key : contextObj.keySet()) {
            Object raw = contextObj.get(key);
            // Context values are DynamicValue — could be literal, DataBinding, or FunctionCall
            context.put(key, DynamicTypeDeserializer.parseDynamicValue(raw));
        }
        return context;
    }

    private static Map<String, Object> parseArgs(JSONObject argsObj) {
        if (argsObj == null) return Map.of();
        Map<String, Object> args = new HashMap<>();
        for (String key : argsObj.keySet()) {
            args.put(key, DynamicTypeDeserializer.parseDynamicValue(argsObj.get(key)));
        }
        return args;
    }
}

