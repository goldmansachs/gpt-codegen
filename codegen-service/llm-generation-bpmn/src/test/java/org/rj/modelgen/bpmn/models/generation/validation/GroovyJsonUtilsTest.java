package org.rj.modelgen.bpmn.models.generation.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.rj.modelgen.llm.util.Util;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.rj.modelgen.bpmn.models.generation.validation.GroovyJsonUtils.*;

public class GroovyJsonUtilsTest {

    private static JsonNode json(String raw) {
        try {
            return Util.getObjectMapper().readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void toGroovyLiteral_serializesScalarsAndEmptyObject() {
        assertEquals("(\"\"\"hi\"\"\").toString()", toGroovyLiteral(new JsonScalar("hi")));
        assertEquals("42", toGroovyLiteral(new JsonScalar(42)));
        assertEquals("true", toGroovyLiteral(new JsonScalar(true)));
        assertEquals("null", toGroovyLiteral(new JsonNull()));
        assertEquals("[:]", toGroovyLiteral(obj()));
    }

    @Test
    public void toGroovyLiteral_serializesNestedObjectWithEscapedKeys() {
        JsonObj o = put(obj(), "a", new JsonScalar(1));
        assertEquals("[((\"\"\"a\"\"\").toString()): 1]", toGroovyLiteral(o));
    }

    @Test
    public void toGroovyLiteral_serializesArray() {
        JsonArr arr = new JsonArr(List.of(new JsonScalar("x"), new JsonScalar(2)));
        assertEquals("[(\"\"\"x\"\"\").toString(), 2]", toGroovyLiteral(arr));
    }

    @Test
    public void toGroovyLiteral_emitsRawLiteralVerbatim() {
        assertEquals("engineContext.isProd()", toGroovyLiteral(new RawLiteral("engineContext.isProd()")));
    }

    @Test
    public void deepMerge_addsNewKeyWithoutDroppingExisting() {
        JsonObj base = put(obj(), "existing", new JsonScalar("kept"));
        GroovyValue merged = deepMerge(base, json("{\"added\": \"new\"}"));

        String literal = toGroovyLiteral(merged);
        assertEquals(true, literal.contains("existing"));
        assertEquals(true, literal.contains("kept"));
        assertEquals(true, literal.contains("added"));
        assertEquals(true, literal.contains("new"));
    }

    @Test
    public void deepMerge_overrideReplacesExistingScalarLeaf() {
        JsonObj base = put(obj(), "key", new JsonScalar("old"));
        GroovyValue merged = deepMerge(base, json("{\"key\": \"new\"}"));
        assertEquals("[((\"\"\"key\"\"\").toString()): (\"\"\"new\"\"\").toString()]", toGroovyLiteral(merged));
    }

    @Test
    public void deepMerge_nullOverrideCollapsesFieldToGroovyNull() {
        JsonObj base = put(obj(), "key", new JsonScalar("value"));
        GroovyValue merged = deepMerge(base, json("null"));
        assertEquals("null", toGroovyLiteral(merged));
    }

    @Test
    public void deepMerge_arrayOverrideConcatenatesWithBaseArray() {
        JsonArr base = new JsonArr(List.of(new JsonScalar("base-item")));
        GroovyValue merged = deepMerge(base, json("[\"override-item\"]"));
        assertEquals("[(\"\"\"base-item\"\"\").toString(), (\"\"\"override-item\"\"\").toString()]", toGroovyLiteral(merged));
    }

    @Test
    public void deepMerge_missingOverrideReturnsBaseUnchanged() {
        JsonObj base = put(obj(), "key", new JsonScalar("value"));
        GroovyValue merged = deepMerge(base, null);
        assertEquals(base, merged);
    }
}
