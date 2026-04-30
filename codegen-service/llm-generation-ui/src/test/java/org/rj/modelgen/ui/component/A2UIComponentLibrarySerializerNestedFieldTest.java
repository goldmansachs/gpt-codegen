package org.rj.modelgen.ui.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the serializer correctly expands complex nested fields while keeping simple fields inline.
 */
class A2UIComponentLibrarySerializerNestedFieldTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void simpleFieldsRemainInline() {
        var comp = new A2UIComponent("TextBox", "A simple text box.",
                List.of(new A2UIComponent.FieldSpec("label", "DynamicString", "The label", List.of())),
                List.of(new A2UIComponent.FieldSpec("visible", "boolean", "Visibility flag", List.of())),
                false);

        var serializer = new A2UIComponentLibrarySerializer<>();
        String result = serializer.serializeOneComponent(comp);

        // Should use inline comma-separated format
        assertTrue(result.contains("Required: label (DynamicString)"));
        assertTrue(result.contains("Optional: visible (boolean)"));
        assertFalse(result.contains("Required:\n"));
    }

    @Test
    void arrayWithItemPropertiesExpandsToMultiLine() throws Exception {
        JsonNode columnsSchema = MAPPER.readTree("""
            {
              "type": "array",
              "description": "Column definitions for the grid.",
              "items": {
                "type": "object",
                "properties": {
                  "field": { "type": "string", "description": "Row object field name." },
                  "headerName": { "type": "string", "description": "Column header display name." },
                  "editable": { "type": "boolean", "description": "Whether the column is editable." }
                },
                "required": ["field"],
                "additionalProperties": false
              }
            }
            """);

        var requiredFields = List.of(
                new A2UIComponent.FieldSpec("columns", "array<object>", "Column definitions for the grid.", List.of(), columnsSchema)
        );
        var optionalFields = List.of(
                new A2UIComponent.FieldSpec("pageSize", "integer", "Rows per page.", List.of())
        );
        var comp = new A2UIComponent("DataGrid", "A data grid component.", requiredFields, optionalFields, false);

        var serializer = new A2UIComponentLibrarySerializer<>();
        String result = serializer.serializeOneComponent(comp);

        // Required section should be multi-line
        assertTrue(result.contains("Required:\n"), "Expected multi-line Required section");
        assertTrue(result.contains("- columns (array<object>)"));
        assertTrue(result.contains("Each item:"));
        assertTrue(result.contains("- field (string) [required]"));
        assertTrue(result.contains("- headerName (string)"));
        assertTrue(result.contains("additionalProperties: false"));

        // Optional section should remain inline since no complex fields
        assertTrue(result.contains("Optional: pageSize (integer)"));
    }

    @Test
    void arrayWithItemEnumExpandsToItemValues() throws Exception {
        JsonNode gridOptionsSchema = MAPPER.readTree("""
            {
              "type": "array",
              "description": "Feature flags for the grid.",
              "items": {
                "type": "string",
                "enum": ["Enable Editing", "Enable Row Selection", "Enable Add Row"]
              }
            }
            """);

        var optionalFields = List.of(
                new A2UIComponent.FieldSpec("gridOptions", "array<string>", "Feature flags for the grid.", List.of(), gridOptionsSchema)
        );
        var comp = new A2UIComponent("DataGrid", "A grid.", List.of(), optionalFields, false);

        var serializer = new A2UIComponentLibrarySerializer<>();
        String result = serializer.serializeOneComponent(comp);

        assertTrue(result.contains("Optional:\n"));
        assertTrue(result.contains("- gridOptions (array<string>)"));
        assertTrue(result.contains("Item values:"));
        assertTrue(result.contains("\"Enable Editing\""));
        assertTrue(result.contains("\"Enable Row Selection\""));
    }

    @Test
    void rawSchemaIsNullForSimpleFields() throws Exception {
        // Catalog JSON with a simple component
        String catalogJson = """
            {
              "components": {
                "SimpleBox": {
                  "allOf": [
                    {
                      "properties": {
                        "component": { "const": "SimpleBox" },
                        "label": { "$ref": "#/$defs/DynamicString", "description": "The label" }
                      },
                      "required": ["component", "label"]
                    }
                  ]
                }
              }
            }
            """;

        var library = A2UIComponentLibrary.fromCatalogJson(catalogJson, "{}");
        var comp = library.getComponents().get(0);

        assertEquals("SimpleBox", comp.getComponentType());
        assertFalse(comp.getRequiredFields().isEmpty());
        assertNull(comp.getRequiredFields().get(0).rawSchema(), "Simple $ref field should have null rawSchema");
    }

    @Test
    void rawSchemaIsPopulatedForArrayWithItemProperties() throws Exception {
        String catalogJson = """
            {
              "components": {
                "Grid": {
                  "allOf": [
                    {
                      "properties": {
                        "component": { "const": "Grid" },
                        "columns": {
                          "type": "array",
                          "description": "Columns",
                          "items": {
                            "type": "object",
                            "properties": {
                              "field": { "type": "string" }
                            },
                            "required": ["field"]
                          }
                        }
                      },
                      "required": ["component", "columns"]
                    }
                  ]
                }
              }
            }
            """;

        var library = A2UIComponentLibrary.fromCatalogJson(catalogJson, "{}");
        var comp = library.getComponents().get(0);
        var columnsField = comp.getRequiredFields().get(0);

        assertEquals("columns", columnsField.name());
        assertNotNull(columnsField.rawSchema(), "Array with item properties should have rawSchema populated");
    }
}

