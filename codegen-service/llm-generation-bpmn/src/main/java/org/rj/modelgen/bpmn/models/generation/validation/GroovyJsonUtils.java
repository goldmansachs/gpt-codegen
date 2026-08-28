package org.rj.modelgen.bpmn.models.generation.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.rj.modelgen.bpmn.models.generation.validation.BpmnScriptUtils.quotedGroovyString;

/**
 * A minimal JSON-ish value tree used to deep-merge caller-supplied JSON overrides on top of a
 * structurally-built base value, then serialize the result as Groovy literal syntax embeddable in
 * a generated script (e.g. {@code def body = [...]}). Generic - has no knowledge of any specific
 * script's shape.
 */
public final class GroovyJsonUtils {

    private GroovyJsonUtils() {
    }

    public sealed interface GroovyValue permits RawLiteral, JsonScalar, JsonObj, JsonArr, JsonNull {
    }

    // Marks a value as already-rendered Groovy source (e.g. from a field-specific escaper) so
    // toGroovyLiteral emits it verbatim instead of re-quoting it as a plain string.
    public record RawLiteral(String literal) implements GroovyValue {
    }

    public record JsonScalar(Object value) implements GroovyValue {
    }

    public record JsonObj(LinkedHashMap<String, GroovyValue> fields) implements GroovyValue {
    }

    public record JsonArr(List<GroovyValue> items) implements GroovyValue {
    }

    record JsonNull() implements GroovyValue {
    }

    private static final JsonNull NULL = new JsonNull();

    public static JsonObj obj() {
        return new JsonObj(new LinkedHashMap<>());
    }

    public static JsonObj put(JsonObj o, String key, GroovyValue value) {
        o.fields().put(key, value);
        return o;
    }

    private static GroovyValue fromJsonNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return NULL;
        if (node.isObject()) {
            LinkedHashMap<String, GroovyValue> fields = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> fields.put(e.getKey(), fromJsonNode(e.getValue())));
            return new JsonObj(fields);
        }
        if (node.isArray()) {
            List<GroovyValue> items = new ArrayList<>();
            node.forEach(n -> items.add(fromJsonNode(n)));
            return new JsonArr(items);
        }
        if (node.isBoolean()) return new JsonScalar(node.booleanValue());
        if (node.isNumber()) return new JsonScalar(node.numberValue());
        return new JsonScalar(node.asText());
    }

    // Deep merge; override wins for objects and scalars. Arrays are concatenated (base items first,
    // then override items) rather than replaced, so overriding an array-shaped field adds to
    // whatever the base already produced instead of silently discarding it.
    public static GroovyValue deepMerge(GroovyValue base, JsonNode overrides) {
        if (overrides == null || overrides.isMissingNode()) return base;
        if (overrides.isNull()) return NULL;
        if (overrides.isArray()) {
            List<GroovyValue> items = new ArrayList<>();
            if (base instanceof JsonArr baseArr) items.addAll(baseArr.items());
            overrides.forEach(item -> items.add(fromJsonNode(item)));
            return new JsonArr(items);
        }
        if (!overrides.isObject()) return fromJsonNode(overrides);

        LinkedHashMap<String, GroovyValue> merged = base instanceof JsonObj bo
                ? new LinkedHashMap<>(bo.fields())
                : new LinkedHashMap<>();
        overrides.fields().forEachRemaining(e ->
                merged.put(e.getKey(), deepMerge(merged.getOrDefault(e.getKey(), NULL), e.getValue())));
        return new JsonObj(merged);
    }

    // Serializes to Groovy literal syntax; map keys are escaped too since they can come from user JSON.
    public static String toGroovyLiteral(GroovyValue value) {
        if (value instanceof RawLiteral raw) return raw.literal();
        if (value instanceof JsonNull) return "null";
        if (value instanceof JsonScalar scalar) {
            Object v = scalar.value();
            return v instanceof String str ? quotedGroovyString(str) : String.valueOf(v);
        }
        if (value instanceof JsonArr arr) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.items().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(toGroovyLiteral(arr.items().get(i)));
            }
            return sb.append("]").toString();
        }
        JsonObj o = (JsonObj) value;
        if (o.fields().isEmpty()) return "[:]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var e : o.fields().entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("(").append(quotedGroovyString(e.getKey())).append("): ").append(toGroovyLiteral(e.getValue()));
        }
        return sb.append("]").toString();
    }
}
