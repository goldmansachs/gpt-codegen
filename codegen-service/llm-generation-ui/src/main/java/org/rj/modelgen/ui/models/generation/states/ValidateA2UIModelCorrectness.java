package org.rj.modelgen.ui.models.generation.states;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.Error;
import org.rj.modelgen.ui.models.generation.data.UIGenerationModelInputPayload;
import org.rj.modelgen.ui.models.generation.signals.UIGenerationSignals;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates the correctness of the generated A2UI output.
 * Performs structural checks on the final A2UI output before completing the pipeline.
 */
public class ValidateA2UIModelCorrectness extends ModelInterfaceState {
    private static final Logger LOG = LoggerFactory.getLogger(ValidateA2UIModelCorrectness.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Schema primarySchema;

    public ValidateA2UIModelCorrectness() throws Exception {
        super(ValidateA2UIModelCorrectness.class);

        SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(Dialects.getDraft202012(), builder -> {
            // Register URI Mappings (Indirection)
            // Maps the logical URI used in $ref to the physical classpath location
            builder.schemaIdResolvers(resolvers -> resolvers
                    .mapPrefix("https://a2ui.org/specification/v0_9/basic_catalog.json", "classpath:schemas/basic_catalog.json")
                    .mapPrefix("https://a2ui.org/specification/v0_9/common_types.json", "classpath:schemas/common_types.json")
                    .mapPrefix("https://a2ui.org/specification/v0_9/catalog.json", "classpath:schemas/basic_catalog.json")
                    .mapPrefix("basic_catalog.json", "classpath:schemas/basic_catalog.json")
                    .mapPrefix("catalog.json", "classpath:schemas/basic_catalog.json")
                    .mapPrefix("common_types.json", "classpath:schemas/common_types.json")
            );
        });

        this.primarySchema = schemaRegistry.getSchema(SchemaLocation.of("classpath:schemas/server_to_client.json"));
    }

    @Override
    public String getDescription() {
        return "Validate correctness of generated A2UI output";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal input) {
        final String a2uiOutput = getPayload().get(UIGenerationModelInputPayload.UI_OUTPUT);
        final List<String> validationMessages = validateA2uiOutput(a2uiOutput);

        getPayload().put(StandardModelData.ModelValidationMessages, validationMessages);

        // If there are validation errors, signal failure so the a2ui can loop back for regeneration
        if (!validationMessages.isEmpty()) {
            LOG.warn("A2UI validation failed with {} error(s); signalling for regeneration", validationMessages.size());
            return outboundSignal(UIGenerationSignals.OutputValidationFailed)
                    .withPayloadData(StandardModelData.ModelValidationMessages, validationMessages)
                    .mono();
        }

        return outboundSignal(UIGenerationSignals.OutputValidated)
                .withPayloadData(StandardModelData.ModelValidationMessages, validationMessages)
                .mono();
    }

    private String extractSurfaceId(JsonNode messageNode) {
        for (String key : List.of("createSurface", "updateComponents", "updateDataModel", "deleteSurface")) {
            if (messageNode.has(key) && messageNode.get(key).has("surfaceId")) {
                return messageNode.get(key).get("surfaceId").asText();
            }
        }
        return null;
    }

    private void collectChildReferences(JsonNode comp, Set<String> refs) {
        // Check 'child' (single child, e.g., Card, Button)
        if (comp.has("child") && comp.get("child").isTextual()) {
            refs.add(comp.get("child").asText());
        }
        // Check 'children' (ChildList - array form)
        if (comp.has("children") && comp.get("children").isArray()) {
            for (JsonNode childId : comp.get("children")) {
                if (childId.isTextual()) refs.add(childId.asText());
            }
        }
        // Check 'children' (ChildList - template form)
        if (comp.has("children") && comp.get("children").isObject()) {
            JsonNode templateChild = comp.get("children").get("componentId");
            if (templateChild != null && templateChild.isTextual()) {
                refs.add(templateChild.asText());
            }
        }
        // Check 'tabs' (Tabs component)
        if (comp.has("tabs") && comp.get("tabs").isArray()) {
            for (JsonNode tab : comp.get("tabs")) {
                if (tab.has("child") && tab.get("child").isTextual()) {
                    refs.add(tab.get("child").asText());
                }
            }
        }
        // Check 'trigger' and 'content' (Modal component)
        if (comp.has("trigger") && comp.get("trigger").isTextual()) {
            refs.add(comp.get("trigger").asText());
        }
        if (comp.has("content") && comp.get("content").isTextual()) {
            refs.add(comp.get("content").asText());
        }
    }

    /**
     * Validates A2UI JSONL output and returns a list of validation messages.
     * Empty list means no issues found.
     */
    public List<String> validateA2uiOutput(String a2uiOutput) {
        final List<String> validationMessages = new ArrayList<>();

        if (a2uiOutput == null || a2uiOutput.isBlank()) {
            validationMessages.add("Generated A2UI output is null or empty");
            return validationMessages;
        }

        final String[] lines = a2uiOutput.strip().split("\\n");
        boolean hasCreateSurface = false;
        boolean hasRootComponent = false;
        final Set<String> definedComponentIds = new HashSet<>();
        final Set<String> referencedChildIds = new HashSet<>();

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            final String line = lines[lineIndex].strip();
            if (line.isEmpty()) continue;

            try {
                final JsonNode messageNode = objectMapper.readTree(line);

                final Set<Error> schemaErrors = new HashSet<>(primarySchema.validate(messageNode));

                for (Error error : schemaErrors) {
                    String surfaceId = extractSurfaceId(messageNode);
                    validationMessages.add(String.format(
                            "{\"error\":{\"code\":\"VALIDATION_FAILED\"," +
                                    "\"surfaceId\":\"%s\"," +
                                    "\"path\":\"%s\"," +
                                    "\"message\":\"%s\"}}",
                            surfaceId != null ? surfaceId : "unknown",
                            error.getEvaluationPath(),
                            error.getMessage().replace("\"", "\\\"")
                    ));
                }

                if (!messageNode.has("version") || !"v0.9".equals(messageNode.get("version").asText())) {
                    validationMessages.add("Line " + (lineIndex + 1) + ": Missing or incorrect 'version' field. Must be 'v0.9'.");
                }

                if (messageNode.has("createSurface")) {
                    hasCreateSurface = true;
                } else if (!hasCreateSurface) {
                    validationMessages.add("Line " + (lineIndex + 1) +
                            ": Message sent before createSurface. A surface must be created first.");
                }

                if (messageNode.has("updateComponents")) {
                    JsonNode components = messageNode.get("updateComponents").get("components");
                    if (components != null && components.isArray()) {
                        for (JsonNode comp : components) {
                            String compId = comp.has("id") ? comp.get("id").asText() : null;
                            if (compId != null) {
                                if (!definedComponentIds.add(compId)) {
                                    validationMessages.add("Line " + (lineIndex + 1) + ": Duplicate component id: '" + compId + "'");
                                }
                                if ("root".equals(compId)) {
                                    hasRootComponent = true;
                                }
                            }
                            collectChildReferences(comp, referencedChildIds);
                        }
                    }
                }

            } catch (Exception e) {
                validationMessages.add("Line " + (lineIndex + 1) + ": Invalid JSON - " + e.getMessage());
            }
        }

        if (!hasRootComponent) {
            validationMessages.add("No component with id 'root' found. Exactly one component must have id 'root'.");
        }

        referencedChildIds.removeAll(definedComponentIds);
        if (!referencedChildIds.isEmpty()) {
            validationMessages.add("Warning: The following child IDs are referenced but never defined: " + referencedChildIds);
        }

        return validationMessages;
    }

    public static void main(String[] args) {
        try {
            final boolean readModelFromFile = true;
            ValidateA2UIModelCorrectness validator = new ValidateA2UIModelCorrectness();

            String a2uiOutput;
            if (readModelFromFile) {
                a2uiOutput = new String(
                        ValidateA2UIModelCorrectness.class.getClassLoader()
                                .getResourceAsStream("a2ui/testModel.jsonl")
                                .readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8
                );
            } else {
                // Example A2UI model for testing
                a2uiOutput = """
                    {"version":"v0.9","createSurface":{"surfaceId":"s1","rootComponentId":"root"}}
                    {"version":"v0.9","updateComponents":{"surfaceId":"s1","components":[{"id":"root","type":"Container","children":["child1"]},{"id":"child1","type":"Text","text":"Hello"}]}}
                    """;
            }

            List<String> messages = validator.validateA2uiOutput(a2uiOutput);

            if (messages.isEmpty()) {
                System.out.println("Validation passed — no issues found.");
            } else {
                System.out.println("Validation found " + messages.size() + " issue(s):");
                for (int i = 0; i < messages.size(); i++) {
                    System.out.println("  " + (i + 1) + ". " + messages.get(i));
                }
            }

            System.exit(messages.isEmpty() ? 0 : 1);
        } catch (Exception e) {
            System.err.println("Failed to initialise validator: " + e.getMessage());
            System.exit(2);
        }
    }
}
