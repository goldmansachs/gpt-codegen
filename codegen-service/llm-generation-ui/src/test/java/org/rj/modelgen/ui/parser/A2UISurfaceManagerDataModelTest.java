package org.rj.modelgen.ui.parser;

import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class A2UISurfaceManagerDataModelTest {

    private A2UISurfaceManager manager;
    private Method applyMethod;

    @BeforeEach
    void setUp() throws Exception {
        manager = new A2UISurfaceManager();
        applyMethod = A2UISurfaceManager.class.getDeclaredMethod(
                "applyDataModelUpdate", Map.class, String.class, Object.class);
        applyMethod.setAccessible(true);
    }

    private void apply(Map<String, Object> model, String path, Object value) throws Exception {
        applyMethod.invoke(manager, model, path, value);
    }

    // 1. Replace entire model with path = null
    @Test
    void replaceEntireModelWithNullPath() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("old", "data");

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("a", 1);

        apply(model, null, newValue);

        Assertions.assertEquals(Map.of("a", 1), model);
    }

    // 2. Replace entire model with path = "/"
    @Test
    void replaceEntireModelWithSlashPath() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("old", "data");

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("b", 2);

        apply(model, "/", newValue);

        Assertions.assertEquals(Map.of("b", 2), model);
    }

    // 3. Wipe model with null value
    @Test
    void wipeModelWithNullValue() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("key", "value");

        apply(model, "/", null);

        Assertions.assertTrue(model.isEmpty());
    }

    // 4. Set top-level key
    @Test
    void setTopLevelKey() throws Exception {
        Map<String, Object> model = new HashMap<>();

        apply(model, "/foo", 42);

        Assertions.assertEquals(42, model.get("foo"));
    }

    // 5. Set nested key
    @Test
    void setNestedKey() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("a", new HashMap<>(Map.of("existing", "val")));

        apply(model, "/a/b", "hello");

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) model.get("a");
        Assertions.assertEquals("hello", nested.get("b"));
    }

    // 6. Remove key with null value
    @Test
    void removeKeyWithNullValue() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("foo", "bar");

        apply(model, "/foo", null);

        Assertions.assertFalse(model.containsKey("foo"));
    }

    // 7. RFC 6901 escaping: ~1 -> /
    @Test
    void rfc6901SlashEscaping() throws Exception {
        Map<String, Object> model = new HashMap<>();

        apply(model, "/a~1b", 1);

        Assertions.assertEquals(1, model.get("a/b"));
    }

    // 8. RFC 6901 tilde escaping: ~0 -> ~
    @Test
    void rfc6901TildeEscaping() throws Exception {
        Map<String, Object> model = new HashMap<>();

        apply(model, "/m~0n", 1);

        Assertions.assertEquals(1, model.get("m~n"));
    }

    // 9. List index set
    @Test
    void listIndexSet() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("arr", new ArrayList<>(List.of("a", "b", "c")));

        apply(model, "/arr/1", 99);

        @SuppressWarnings("unchecked")
        List<Object> arr = (List<Object>) model.get("arr");
        Assertions.assertEquals(99, arr.get(1));
    }

    // 10. List append with "-"
    @Test
    void listAppendWithDash() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("arr", new ArrayList<>(List.of("a", "b")));

        apply(model, "/arr/-", "new");

        @SuppressWarnings("unchecked")
        List<Object> arr = (List<Object>) model.get("arr");
        Assertions.assertEquals(3, arr.size());
        Assertions.assertEquals("new", arr.get(2));
    }

    // 11. List index remove
    @Test
    void listIndexRemove() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("arr", new ArrayList<>(List.of("a", "b", "c")));

        apply(model, "/arr/0", null);

        @SuppressWarnings("unchecked")
        List<Object> arr = (List<Object>) model.get("arr");
        Assertions.assertEquals(2, arr.size());
        Assertions.assertEquals("b", arr.get(0));
    }

    // 12. Invalid path (no leading /)
    @Test
    void invalidPathNoLeadingSlash() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("existing", "value");

        apply(model, "foo", 1);

        // No change; error logged
        Assertions.assertEquals(Map.of("existing", "value"), model);
    }

    // 13. Path segment not found
    @Test
    void pathSegmentNotFound() throws Exception {
        Map<String, Object> model = new HashMap<>();

        apply(model, "/nonexistent/child", 1);

        // No change; error logged
        Assertions.assertFalse(model.containsKey("nonexistent"));
    }

    // 14. Invalid array index
    @Test
    void invalidArrayIndex() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("arr", new ArrayList<>(List.of("a", "b")));

        apply(model, "/arr/999", 1);

        // No change; error logged
        @SuppressWarnings("unchecked")
        List<Object> arr = (List<Object>) model.get("arr");
        Assertions.assertEquals(List.of("a", "b"), arr);
    }

    // 15. unwrapValue converts JSONObject
    @Test
    void unwrapValueConvertsJSONObject() throws Exception {
        Map<String, Object> model = new HashMap<>();

        apply(model, "/key", new JSONObject("{\"x\":1}"));

        Object result = model.get("key");
        Assertions.assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        Assertions.assertEquals(1, resultMap.get("x"));
    }
}

