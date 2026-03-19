package org.rj.modelgen.ui.model.a2ui.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.type.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DynamicTypeDeserializer {

    private DynamicTypeDeserializer() {}

    /**
     * Deserializes a DynamicString from a raw value obtained via
     * {@code JSONObject.get(key)}.
     *
     * Per common_types.json, DynamicString is a oneOf:
     *   - a literal string
     *   - a DataBinding object with a "path"
     *   - a FunctionCall object with "call" and returnType "string"
     */
    public static DynamicString deserializeDynamicString(Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof String s) {
            return new LiteralString(s);
        }
        if (raw instanceof JSONObject obj) {
            if (obj.has("path")) {
                return new DataBinding(obj.getString("path"));
            }
            if (obj.has("call")) {
                return new StringFunctionCall(
                        obj.getString("call"),
                        parseArgs(obj.optJSONObject("args")),
                        obj.optString("returnType", "string")
                );
            }
            throw new IllegalArgumentException(
                    "DynamicString object must have 'path' or 'call': " + obj);
        }
        throw new IllegalArgumentException(
                "Cannot deserialize DynamicString from: " + raw.getClass().getSimpleName());
    }

    /**
     * Deserializes a DynamicBoolean from a raw value.
     *
     * Per common_types.json, DynamicBoolean is a oneOf:
     *   - a literal boolean
     *   - a DataBinding object with a "path"
     *   - a FunctionCall object with "call" and returnType "boolean"
     */
    public static DynamicBoolean deserializeDynamicBoolean(Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof Boolean b) {
            return new LiteralBoolean(b);
        }
        if (raw instanceof JSONObject obj) {
            if (obj.has("path")) {
                return new DataBinding(obj.getString("path"));
            }
            if (obj.has("call")) {
                return new BooleanFunctionCall(
                        obj.getString("call"),
                        parseArgs(obj.optJSONObject("args")),
                        obj.optString("returnType", "boolean")
                );
            }
            throw new IllegalArgumentException(
                    "DynamicBoolean object must have 'path' or 'call': " + obj);
        }
        throw new IllegalArgumentException(
                "Cannot deserialize DynamicBoolean from: " + raw.getClass().getSimpleName());
    }

    /**
     * Deserializes a DynamicNumber from a raw value.
     *
     * Per common_types.json, DynamicNumber is a oneOf:
     *   - a literal number
     *   - a DataBinding object with a "path"
     *   - a FunctionCall object with "call" and returnType "number"
     */
    public static DynamicNumber deserializeDynamicNumber(Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof Number n) {
            return new LiteralNumber(n.doubleValue());
        }
        if (raw instanceof JSONObject obj) {
            if (obj.has("path")) {
                return new DataBinding(obj.getString("path"));
            }
            if (obj.has("call")) {
                return new NumberFunctionCall(
                        obj.getString("call"),
                        parseArgs(obj.optJSONObject("args")),
                        obj.optString("returnType", "number")
                );
            }
            throw new IllegalArgumentException(
                    "DynamicNumber object must have 'path' or 'call': " + obj);
        }
        throw new IllegalArgumentException(
                "Cannot deserialize DynamicNumber from: " + raw.getClass().getSimpleName());
    }

    /**
     * Deserializes a DynamicStringList from a raw value.
     *
     * Per common_types.json, DynamicStringList is a oneOf:
     *   - a literal array of strings
     *   - a DataBinding object with a "path"
     *   - a FunctionCall object with "call" and returnType "array"
     */
    public static DynamicStringList deserializeDynamicStringList(Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof JSONArray arr) {
            List<String> values = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                values.add(arr.getString(i));
            }
            return new LiteralStringList(values);
        }
        if (raw instanceof JSONObject obj) {
            if (obj.has("path")) {
                return new DataBinding(obj.getString("path"));
            }
            if (obj.has("call")) {
                return new ArrayFunctionCall(
                        obj.getString("call"),
                        parseArgs(obj.optJSONObject("args")),
                        obj.optString("returnType", "array")
                );
            }
            throw new IllegalArgumentException(
                    "DynamicStringList object must have 'path' or 'call': " + obj);
        }
        throw new IllegalArgumentException(
                "Cannot deserialize DynamicStringList from: " + raw.getClass().getSimpleName());
    }

    /**
     * Deserializes a ChildList from a raw value.
     *
     * Per common_types.json, ChildList is a oneOf:
     *   - an array of ComponentId strings (static child list)
     *   - an object with "componentId" and "path" (template child list)
     */
    public static ChildList deserializeChildList(Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof JSONArray arr) {
            List<String> ids = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                ids.add(arr.getString(i));
            }
            return new StaticChildList(ids);
        }
        if (raw instanceof JSONObject obj) {
            return new TemplateChildList(
                    obj.getString("componentId"),
                    obj.getString("path")
            );
        }
        throw new IllegalArgumentException(
                "Cannot deserialize ChildList from: " + raw.getClass().getSimpleName());
    }

    /**
     * Deserializes an Action from a JSONObject.
     *
     * Per common_types.json, Action is a oneOf:
     *   - an object with "event" (server-side event)
     *   - an object with "functionCall" (local client-side function)
     */
    public static Action deserializeAction(JSONObject raw) {
        if (raw == null) {
            return null;
        }
        if (raw.has("event")) {
            JSONObject eventObj = raw.getJSONObject("event");
            String name = eventObj.getString("name");
            Map<String, Object> context = null;
            if (eventObj.has("context")) {
                context = eventObj.getJSONObject("context").toMap();
            }
            return new EventAction(name, context);
        }
        if (raw.has("functionCall")) {
            JSONObject fcObj = raw.getJSONObject("functionCall");
            return new FunctionCallAction(
                    fcObj.getString("call"),
                    parseArgs(fcObj.optJSONObject("args")),
                    fcObj.optString("returnType", "boolean")
            );
        }
        throw new IllegalArgumentException("Action must have 'event' or 'functionCall': " + raw);
    }

    /**
     * Deserializes a CheckRule from a JSONObject.
     *
     * Per common_types.json, CheckRule has:
     *   - "condition": a DynamicBoolean
     *   - "message": a string
     */
    public static CheckRule deserializeCheckRule(JSONObject raw) {
        if (raw == null) {
            return null;
        }
        DynamicBoolean condition = deserializeDynamicBoolean(raw.get("condition"));
        String message = raw.getString("message");
        return new CheckRule(condition, message);
    }

    /**
     * Deserializes a list of CheckRules from a JSONArray.
     */
    public static List<CheckRule> deserializeCheckRules(JSONArray arr) {
        if (arr == null) {
            return null;
        }
        List<CheckRule> rules = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            rules.add(deserializeCheckRule(arr.getJSONObject(i)));
        }
        return rules;
    }

    /**
     * Parses a DynamicValue — the broadest union type in common_types.json.
     * DynamicValue is a oneOf: string, number, boolean, array,
     * DataBinding (object with "path"), or FunctionCall (object with "call").
     */
    public static Object parseDynamicValue(Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof String || raw instanceof Boolean || raw instanceof Number) {
            return raw;
        }
        if (raw instanceof JSONArray arr) {
            List<Object> list = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                list.add(parseDynamicValue(arr.get(i)));
            }
            return List.copyOf(list);
        }
        if (raw instanceof JSONObject obj) {
            if (obj.has("path")) {
                return new DataBinding(obj.getString("path"));
            }
            if (obj.has("call")) {
                return new FunctionCall(
                        obj.getString("call"),
                        parseArgs(obj.optJSONObject("args")),
                        obj.optString("returnType", "boolean")
                );
            }

            return obj.toMap();
        }

        throw new IllegalArgumentException(
                "Cannot parse DynamicValue from: " + raw.getClass().getSimpleName());
    }

    /**
     * Parses the "args" object of a FunctionCall into a Map.
     * Per common_types.json, each arg value can be a DynamicValue
     * (string, number, boolean, array, DataBinding, or FunctionCall)
     * or a literal object.
     */
    private static Map<String, Object> parseArgs(JSONObject argsObj) {
        if (argsObj == null) {
            return Map.of();
        }
        Map<String, Object> args = new HashMap<>();
        for (String key : argsObj.keySet()) {
            args.put(key, parseDynamicValue(argsObj.get(key)));
        }
        return Map.copyOf(args); // immutable copy
    }
}
