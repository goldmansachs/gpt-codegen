package org.rj.modelgen.ui.models.generation.states;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Validates the correctness of the generated A2UI output.
 * Performs structural checks on the final A2UI output before completing the pipeline.
 *
 * <p>Schema validation is decomposed: large {@code updateComponents} messages are split
 * into an envelope (validated against the primary schema with a stub components array)
 * and individual components (validated against the component sub-schema in parallel using
 * a bounded thread pool). This avoids combinatorial explosion in {@code oneOf}/{@code anyOf}
 * resolution when many components are present.</p>
 */
public class ValidateA2UIModelCorrectness extends ModelInterfaceState {
    private static final Logger LOG = LoggerFactory.getLogger(ValidateA2UIModelCorrectness.class);
    private static final String BASIC_CATALOG_RESOURCE = "classpath:schemas/basic_catalog.json";
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String UNEVALUATED_PROPERTIES = "unevaluatedProperties";
    private static final String SUPPORTED_A2UI_VERSION = "0.9";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Schema primarySchema;
    private final Schema componentSchema;

    /**
     * Component name to its index in the catalog's {@code anyComponent} oneOf. Used to report only
     * the errors from the branch a component actually targets - see {@link #focusComponentErrors}.
     */
    private final Map<String, Integer> componentBranchIndex;

    // JSON field name constants
    private static final String FIELD_CREATE_SURFACE = "createSurface";
    private static final String FIELD_UPDATE_COMPONENTS = "updateComponents";
    private static final String FIELD_UPDATE_DATA_MODEL = "updateDataModel";
    private static final String FIELD_DELETE_SURFACE = "deleteSurface";
    private static final String FIELD_COMPONENTS = "components";
    private static final String FIELD_CHILD = "child";
    private static final String FIELD_CHILDREN = "children";
    private static final String FIELD_TRIGGER = "trigger";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_ACTION = "action";
    private static final String FIELD_EVENT = "event";
    private static final String FIELD_VALUES = "values";
    private static final String LINE_PREFIX = "Line ";

    /**
     * Threshold above which an {@code updateComponents} message is decomposed
     * into envelope + individual component validations.
     */
    private static final int COMPONENT_DECOMPOSITION_THRESHOLD = 1;

    /**
     * Per-component validation timeout.
     */
    private static final Duration COMPONENT_VALIDATION_TIMEOUT = Duration.ofSeconds(5);

    private static final List<String> MESSAGE_TYPE_KEYS = List.of(
            FIELD_CREATE_SURFACE, FIELD_UPDATE_COMPONENTS, FIELD_UPDATE_DATA_MODEL, FIELD_DELETE_SURFACE);

    /**
     * Default catalog URI prefix mappings used when no custom catalogs are provided.
     */
    private static final Map<String, String> DEFAULT_CATALOG_MAPPINGS = Map.of(
            "https://a2ui.org/specification/v0_9/basic_catalog.json", BASIC_CATALOG_RESOURCE,
            "https://a2ui.org/specification/v0_9/catalog.json",       BASIC_CATALOG_RESOURCE,
            "basic_catalog.json",                                      BASIC_CATALOG_RESOURCE,
            "catalog.json",                                            BASIC_CATALOG_RESOURCE,
            "https://a2ui.org/specification/v0_9/common_types.json",  "classpath:schemas/common_types.json",
            "common_types.json",                                       "classpath:schemas/common_types.json"
    );

    /**
     * Immutable result of validating a single component, carrying both schema-level
     * and structural validation errors.
     */
    private record ComponentValidationResult(
            int index, String compId, Set<Error> schemaErrors, List<String> structuralErrors, long elapsedMs) {}

    /**
     * Mutable context accumulated across lines during validation.
     */
    private static class LineValidationContext {
        boolean hasCreateSurface;
        boolean hasRootComponent;
        final Set<String> definedComponentIds = new HashSet<>();
        final Set<String> referencedChildIds = new HashSet<>();
        final List<JsonNode> allComponents = new ArrayList<>();
    }

    /**
     * Default constructor — uses the standard A2UI catalog mappings.
     */
    public ValidateA2UIModelCorrectness() {
        this(DEFAULT_CATALOG_MAPPINGS);
    }

    /**
     * Constructor that merges additional custom catalog URI mappings on top of the defaults.
     * Subclasses can call this to register extra or replacement catalog schemas.
     *
     * @param additionalCatalogMappings extra URI prefix → classpath resource mappings
     */
    protected ValidateA2UIModelCorrectness(Map<String, String> additionalCatalogMappings) {
        super(ValidateA2UIModelCorrectness.class);

        final Map<String, String> mergedMappings = new LinkedHashMap<>(DEFAULT_CATALOG_MAPPINGS);
        mergedMappings.putAll(additionalCatalogMappings);

        long start = System.nanoTime();
        SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(Dialects.getDraft202012(), builder ->
                builder.schemaIdResolvers(resolvers -> mergedMappings.forEach(resolvers::mapPrefix))
        );
        LOG.info("SchemaRegistry init: {}ms", (System.nanoTime() - start) / 1_000_000);

        start = System.nanoTime();
        this.primarySchema = schemaRegistry.getSchema(SchemaLocation.of("classpath:schemas/server_to_client.json"));
        LOG.info("Primary schema load: {}ms", (System.nanoTime() - start) / 1_000_000);

        // Determine the effective catalog resource — use the merged mapping for the
        // canonical basic catalog key, falling back to the default if not overridden.
        String effectiveCatalogResource = mergedMappings.getOrDefault(
                "basic_catalog.json", BASIC_CATALOG_RESOURCE);

        this.componentBranchIndex = loadComponentBranchIndex(effectiveCatalogResource);

        start = System.nanoTime();
        this.componentSchema = schemaRegistry.getSchema(
                SchemaLocation.of(effectiveCatalogResource + "#/$defs/anyComponent")
        );
        LOG.info("Component sub-schema load: {}ms (catalog={})", (System.nanoTime() - start) / 1_000_000, effectiveCatalogResource);
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

        // If there are validation errors, signal failure so the A2UI can loop back for regeneration
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

    /**
     * Validates A2UI JSONL output and returns a list of validation messages.
     * Empty list means no issues found.
     */
    public List<String> validateA2uiOutput(String a2uiOutput) {
        if (a2uiOutput == null || a2uiOutput.isBlank()) {
            return List.of("Generated A2UI output is null or empty");
        }

        final List<String> lines = a2uiOutput.strip().lines().toList();
        final List<String> validationMessages = new ArrayList<>();
        final LineValidationContext ctx = new LineValidationContext();

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            final String line = lines.get(lineIndex).strip();
            if (!line.isEmpty()) {
                validateSingleLine(line, lineIndex, ctx, validationMessages);
            }
        }

        validateComponentTree(ctx, validationMessages);
        validateBehaviourModifierReferences(ctx, validationMessages);
        return validationMessages;
    }

    private void validateSingleLine(String line, int lineIndex, LineValidationContext ctx,
                                    List<String> validationMessages) {
        try {
            final JsonNode messageNode = objectMapper.readTree(line);

            validationMessages.addAll(validateLineSchema(messageNode, lineIndex));
            validateVersionField(messageNode, lineIndex, validationMessages);
            validateSurfaceOrdering(messageNode, lineIndex, ctx, validationMessages);
            collectComponentIds(messageNode, lineIndex, ctx, validationMessages);
        } catch (Exception e) {
            validationMessages.add(LINE_PREFIX + (lineIndex + 1) + ": Invalid JSON - " + e.getMessage());
        }
    }

    private void validateVersionField(JsonNode messageNode, int lineIndex, List<String> validationMessages) {
        if (!messageNode.has("version") || !String.format("v%s", SUPPORTED_A2UI_VERSION).equals(messageNode.get("version").asText())) {
            validationMessages.add(String.format("Line %d: Missing or incorrect 'version' field. Must be 'v%s'.",
                    lineIndex + 1, SUPPORTED_A2UI_VERSION));
        }
    }

    private void validateSurfaceOrdering(JsonNode messageNode, int lineIndex, LineValidationContext ctx,
                                         List<String> validationMessages) {
        if (messageNode.has(FIELD_CREATE_SURFACE)) {
            ctx.hasCreateSurface = true;
        } else if (!ctx.hasCreateSurface) {
            validationMessages.add(LINE_PREFIX + (lineIndex + 1) +
                    ": Message sent before createSurface. A surface must be created first.");
        }
    }

    private void collectComponentIds(JsonNode messageNode, int lineIndex, LineValidationContext ctx,
                                     List<String> validationMessages) {
        if (!messageNode.has(FIELD_UPDATE_COMPONENTS)) return;
        JsonNode components = messageNode.get(FIELD_UPDATE_COMPONENTS).get(FIELD_COMPONENTS);
        if (components == null || !components.isArray()) return;

        for (JsonNode comp : components) {
            String compId = comp.has("id") ? comp.get("id").asText() : null;
            if (compId != null) {
                if (!ctx.definedComponentIds.add(compId)) {
                    validationMessages.add(LINE_PREFIX + (lineIndex + 1) +
                            ": Duplicate component id: '" + compId + "'");
                }
                if ("root".equals(compId)) {
                    ctx.hasRootComponent = true;
                }
            }
            ctx.allComponents.add(comp);
            collectChildReferences(comp, ctx.referencedChildIds);
        }
    }

    private void validateComponentTree(LineValidationContext ctx, List<String> validationMessages) {
        if (!ctx.hasRootComponent) {
            validationMessages.add("No component with id 'root' found. Exactly one component must have id 'root'.");
        }

        Set<String> unreferencedChildIds = new HashSet<>(ctx.referencedChildIds);
        unreferencedChildIds.removeAll(ctx.definedComponentIds);
        if (!unreferencedChildIds.isEmpty()) {
            validationMessages.add("Warning: The following child IDs are referenced but never defined: " +
                    unreferencedChildIds);
        }
    }

    /**
     * Validates that behaviour modifier rules (visibility, componentRequired, disabled)
     * only reference component IDs that actually exist in the model.
     */
    private void validateBehaviourModifierReferences(LineValidationContext ctx, List<String> validationMessages) {
        final List<String> modifierNames = List.of("visibility", "componentRequired", "disabled");

        for (JsonNode comp : ctx.allComponents) {
            String compId = comp.has("id") ? comp.get("id").asText() : "unknown";

            for (String modifier : modifierNames) {
                if (!comp.has(modifier)) continue;

                JsonNode rules = comp.path(modifier).path("args").path("rules");
                if (!rules.isArray()) continue;

                for (JsonNode rule : rules) {
                    JsonNode checks = rule.path("checks");
                    if (!checks.isArray()) continue;

                    for (JsonNode check : checks) {
                        String input = check.path("input").asText(null);
                        if (input == null || input.isBlank()) continue;

                        if (!ctx.definedComponentIds.contains(input)) {
                            LOG.warn("Component '{}': {} rule references non-existent id '{}'", compId, modifier, input);
                            validationMessages.add(String.format(
                                    "Component '%s': %s rule references non-existent component id '%s'. " +
                                    "The 'input' field in rule checks must reference the 'id' of an existing component. " +
                                    "Available component ids: %s",
                                    compId, modifier, input, ctx.definedComponentIds));
                        }
                    }
                }
            }
        }
    }

    private void collectChildReferences(JsonNode comp, Set<String> refs) {
        collectSingleChildRef(comp, FIELD_CHILD, refs);
        collectChildListRefs(comp, refs);
        collectTabChildRefs(comp, refs);
        collectSingleChildRef(comp, FIELD_TRIGGER, refs);
        collectSingleChildRef(comp, FIELD_CONTENT, refs);
    }

    private void collectSingleChildRef(JsonNode comp, String fieldName, Set<String> refs) {
        if (comp.has(fieldName) && comp.get(fieldName).isTextual()) {
            refs.add(comp.get(fieldName).asText());
        }
    }

    private void collectChildListRefs(JsonNode comp, Set<String> refs) {
        if (!comp.has(FIELD_CHILDREN)) return;
        JsonNode childrenNode = comp.get(FIELD_CHILDREN);
        if (childrenNode.isArray()) {
            for (JsonNode childId : childrenNode) {
                if (childId.isTextual()) refs.add(childId.asText());
            }
        } else if (childrenNode.isObject()) {
            JsonNode templateChild = childrenNode.get("componentId");
            if (templateChild != null && templateChild.isTextual()) {
                refs.add(templateChild.asText());
            }
        }
    }

    private void collectTabChildRefs(JsonNode comp, Set<String> refs) {
        if (!comp.has("tabs") || !comp.get("tabs").isArray()) return;
        for (JsonNode tab : comp.get("tabs")) {
            collectSingleChildRef(tab, FIELD_CHILD, refs);
        }
    }

    /**
     * Validates a single line's JSON against the schema. For {@code updateComponents}
     * messages exceeding the decomposition threshold, the message is split into:
     * <ol>
     *   <li>An envelope with an empty components array (validated against the primary schema)</li>
     *   <li>Each component individually (validated against the component sub-schema in parallel)</li>
     * </ol>
     *
     * @return collected schema error descriptions
     */
    private List<String> validateLineSchema(JsonNode messageNode, int lineIndex) {
        final String surfaceId = extractSurfaceId(messageNode);
        final String surfaceLabel = surfaceId != null ? surfaceId : "unknown";

        if (isDecomposableUpdateComponents(messageNode)) {
            return validateDecomposed(messageNode, lineIndex, surfaceLabel);
        }
        return validateWhole(messageNode, lineIndex, surfaceLabel);
    }

    private String extractSurfaceId(JsonNode messageNode) {
        for (String key : MESSAGE_TYPE_KEYS) {
            if (messageNode.has(key) && messageNode.get(key).has("surfaceId")) {
                return messageNode.get(key).get("surfaceId").asText();
            }
        }
        return null;
    }

    private boolean isDecomposableUpdateComponents(JsonNode messageNode) {
        if (!messageNode.has(FIELD_UPDATE_COMPONENTS)) return false;
        JsonNode components = messageNode.path(FIELD_UPDATE_COMPONENTS).path(FIELD_COMPONENTS);
        return components.isArray() && components.size() > COMPONENT_DECOMPOSITION_THRESHOLD;
    }

    /**
     * Validates the full message as-is (used for simple/small messages like
     * createSurface, updateDataModel, deleteSurface, or small updateComponents).
     */
    private List<String> validateWhole(JsonNode messageNode, int lineIndex, String surfaceLabel) {
        long start = System.nanoTime();
        Set<Error> schemaErrors = new HashSet<>(primarySchema.validate(messageNode));
        LOG.info("Line {}: whole validate={}ms, errors={}", lineIndex + 1,
                (System.nanoTime() - start) / 1_000_000, schemaErrors.size());
        return formatErrors(schemaErrors, surfaceLabel);
    }

    /**
     * Decomposes an {@code updateComponents} message and validates envelope + components separately.
     * The envelope is validated with a stub components array against the primary schema.
     * Each component is validated individually against the component sub-schema using a
     * bounded thread pool sized to the available processors.
     *
     * <p>To avoid combinatorial explosion caused by deeply nested {@code FunctionCall} references
     * inside {@code checks} and {@code action} fields, these fields are stripped from each component
     * before schema validation. The stripped fields are then validated programmatically
     * rather than via the schema validator.</p>
     */
    private List<String> validateDecomposed(JsonNode messageNode, int lineIndex, String surfaceLabel) {
        final JsonNode components = messageNode.path(FIELD_UPDATE_COMPONENTS).path(FIELD_COMPONENTS);
        final int componentCount = components.size();

        // 1. Validate envelope with a single-element stub to satisfy minItems:1
        long envelopeStart = System.nanoTime();
        ObjectNode envelope = messageNode.deepCopy();
        ObjectNode stubComponent = objectMapper.createObjectNode();
        stubComponent.put("id", "__envelope_stub__");
        stubComponent.put("component", "Text");
        stubComponent.put("text", "stub");
        ((ObjectNode) envelope.get(FIELD_UPDATE_COMPONENTS)).set(FIELD_COMPONENTS,
                objectMapper.createArrayNode().add(stubComponent));
        Set<Error> envelopeErrors = new HashSet<>(primarySchema.validate(envelope));
        LOG.info("Line {}: envelope validate={}ms, errors={}",
                lineIndex + 1, (System.nanoTime() - envelopeStart) / 1_000_000, envelopeErrors.size());
        final List<String> errors = new ArrayList<>(formatErrors(envelopeErrors, surfaceLabel));

        // 2. Validate each component concurrently using a bounded thread pool.
        //    Before schema validation, strip 'checks' and 'action' fields to avoid recursive
        //    FunctionCall oneOf explosion. These are validated structurally afterward.
        long compStart = System.nanoTime();
        final int parallelism = Math.min(componentCount, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<ComponentValidationResult>> futures = new ArrayList<>(componentCount);

            for (int i = 0; i < componentCount; i++) {
                final int compIndex = i;
                final JsonNode comp = components.get(i);
                futures.add(executor.submit(() -> validateSingleComponent(comp, compIndex)));
            }

            for (int i = 0; i < futures.size(); i++) {
                collectComponentResult(futures.get(i), components.get(i), i, lineIndex, surfaceLabel, errors);
            }
        } finally {
            shutdownExecutor(executor);
        }

        long totalComp = (System.nanoTime() - compStart) / 1_000_000;
        LOG.info("Line {}: {} components validated in {}ms (parallel)", lineIndex + 1, componentCount, totalComp);

        return errors;
    }

    /**
     * Validates a single component by:
     * <ol>
     *   <li>Stripping {@code checks} (always optional, contains recursive FunctionCall refs)</li>
     *   <li>Replacing {@code action} with a lightweight stub (required on Button, but its nested
     *       {@code context} DynamicValue refs can also trigger explosion)</li>
     *   <li>Schema-validating the modified component against the component sub-schema</li>
     *   <li>Structurally validating the original {@code checks} and {@code action} fields programmatically</li>
     * </ol>
     */
    private ComponentValidationResult validateSingleComponent(JsonNode comp, int compIndex) {
        long t = System.nanoTime();
        String compId = comp.has("id") ? comp.get("id").asText() : "index-" + compIndex;

        // Deep copy and neutralize the explosive fields before schema validation
        ObjectNode strippedComp = comp.deepCopy();
        JsonNode originalChecks = strippedComp.remove("checks");
        JsonNode originalAction = strippedComp.remove(FIELD_ACTION);

        // If the component originally had an 'action', replace with a minimal stub that satisfies
        // the Button schema requirement without triggering recursive FunctionCall validation
        if (originalAction != null) {
            ObjectNode stubAction = objectMapper.createObjectNode();
            ObjectNode stubEvent = objectMapper.createObjectNode();
            stubEvent.put("name", "__stub__");
            stubAction.set(FIELD_EVENT, stubEvent);
            strippedComp.set(FIELD_ACTION, stubAction);
        }

        Set<Error> schemaErrors = new HashSet<>(componentSchema.validate(strippedComp));

        // Structurally validate the original fields
        List<String> structuralErrors = new ArrayList<>();
        if (originalChecks != null) {
            structuralErrors.addAll(validateChecksStructurally(originalChecks, compId));
        }
        if (originalAction != null) {
            structuralErrors.addAll(validateActionStructurally(originalAction, compId));
        }

        long elapsed = (System.nanoTime() - t) / 1_000_000;
        return new ComponentValidationResult(compIndex, compId, schemaErrors, structuralErrors, elapsed);
    }

    /**
     * Structurally validates a {@code checks} array without invoking the schema validator.
     * Verifies each CheckRule has the required shape: {@code {"condition": <DynamicBoolean>, "message": <string>}}.
     */
    private List<String> validateChecksStructurally(JsonNode checksNode, String compId) {
        List<String> errors = new ArrayList<>();
        if (!checksNode.isArray()) {
            errors.add(formatStructuralError(compId, "checks", "'checks' must be an array"));
            return errors;
        }

        for (int i = 0; i < checksNode.size(); i++) {
            JsonNode check = checksNode.get(i);
            String path = "checks[" + i + "]";
            if (!check.isObject()) {
                errors.add(formatStructuralError(compId, path, "Each check must be an object"));
                continue;
            }
            if (!check.has("condition")) {
                errors.add(formatStructuralError(compId, path, "Missing required field 'condition'"));
            } else {
                errors.addAll(validateDynamicBooleanStructurally(check.get("condition"), compId, path + ".condition"));
            }
            if (!check.has("message")) {
                errors.add(formatStructuralError(compId, path, "Missing required field 'message'"));
            } else if (!check.get("message").isTextual()) {
                errors.add(formatStructuralError(compId, path, "'message' must be a string"));
            }
        }
        return errors;
    }

    /**
     * Structurally validates an {@code action} field.
     * An Action must be exactly one of: {@code {"event": {...}}} or {@code {"functionCall": {...}}}.
     */
    private List<String> validateActionStructurally(JsonNode actionNode, String compId) {
        List<String> errors = new ArrayList<>();
        if (!actionNode.isObject()) {
            errors.add(formatStructuralError(compId, FIELD_ACTION, "'action' must be an object"));
            return errors;
        }
        boolean hasEvent = actionNode.has(FIELD_EVENT);
        boolean hasFunctionCall = actionNode.has("functionCall");
        if (hasEvent == hasFunctionCall) {
            errors.add(formatStructuralError(compId, FIELD_ACTION,
                    "Action must have exactly one of 'event' or 'functionCall', found " +
                            (hasEvent ? "both" : "neither")));
            return errors;
        }
        if (hasEvent) {
            errors.addAll(validateEventStructurally(actionNode.get(FIELD_EVENT), compId));
        }
        if (hasFunctionCall) {
            errors.addAll(validateFunctionCallStructurally(
                    actionNode.get("functionCall"), compId, "action.functionCall"));
        }
        return errors;
    }

    private List<String> validateEventStructurally(JsonNode event, String compId) {
        List<String> errors = new ArrayList<>();
        if (!event.isObject()) {
            errors.add(formatStructuralError(compId, "action.event", "'event' must be an object"));
        } else if (!event.has("name") || !event.get("name").isTextual()) {
            errors.add(formatStructuralError(compId, "action.event", "Missing required string field 'name'"));
        }
        return errors;
    }

    /**
     * Structurally validates a DynamicBoolean value. Can be: a literal boolean, a data binding
     * {@code {"path": "..."}}, or a FunctionCall with returnType "boolean".
     * Recurses into nested FunctionCall arguments (e.g., and/or/not) without schema validation.
     */
    private List<String> validateDynamicBooleanStructurally(JsonNode node, String compId, String path) {
        List<String> errors = new ArrayList<>();
        if (node.isBoolean()) {
            return errors;
        }
        if (node.isObject()) {
            if (node.has("path")) {
                if (!node.get("path").isTextual()) {
                    errors.add(formatStructuralError(compId, path, "'path' must be a string"));
                }
                return errors;
            }
            if (node.has("call")) {
                errors.addAll(validateFunctionCallStructurally(node, compId, path));
                return errors;
            }
            errors.add(formatStructuralError(compId, path,
                    "DynamicBoolean object must have either 'path' or 'call'"));
            return errors;
        }
        errors.add(formatStructuralError(compId, path,
                "DynamicBoolean must be a boolean, a data binding, or a function call"));
        return errors;
    }

    /**
     * Structurally validates a FunctionCall object.
     * Verifies required fields and recursively validates DynamicBoolean arguments
     * for known logical functions (and, or, not).
     */
    private List<String> validateFunctionCallStructurally(JsonNode node, String compId, String path) {
        List<String> errors = new ArrayList<>();
        if (!node.isObject()) {
            errors.add(formatStructuralError(compId, path, "FunctionCall must be an object"));
            return errors;
        }
        if (!node.has("call") || !node.get("call").isTextual()) {
            errors.add(formatStructuralError(compId, path, "FunctionCall missing required string field 'call'"));
            return errors;
        }

        if (node.has("returnType") && !node.get("returnType").isTextual()) {
            errors.add(formatStructuralError(compId, path, "'returnType' must be a string"));
        }

        if (node.has("args") && node.get("args").isObject()) {
            errors.addAll(validateFunctionArgsStructurally(
                    node.get("call").asText(), node.get("args"), compId, path));
        } else if (!node.has("args")) {
            errors.add(formatStructuralError(compId, path, "FunctionCall missing required field 'args'"));
        }

        return errors;
    }

    /**
     * Validates the arguments of a FunctionCall. For logical operators ({@code and}, {@code or}, {@code not}),
     * recursively validates their nested DynamicBoolean values.
     */
    private List<String> validateFunctionArgsStructurally(String callName, JsonNode args, String compId, String path) {
        List<String> errors = new ArrayList<>();
        switch (callName) {
            case "and", "or" -> {
                if (args.has(FIELD_VALUES) && args.get(FIELD_VALUES).isArray()) {
                    JsonNode values = args.get(FIELD_VALUES);
                    for (int i = 0; i < values.size(); i++) {
                        errors.addAll(validateDynamicBooleanStructurally(
                                values.get(i), compId, path + ".args.values[" + i + "]"));
                    }
                } else {
                    errors.add(formatStructuralError(compId, path + ".args",
                            "'" + callName + "' function requires 'values' array argument"));
                }
            }
            case "not" -> {
                if (args.has("value")) {
                    errors.addAll(validateDynamicBooleanStructurally(
                            args.get("value"), compId, path + ".args.value"));
                } else {
                    errors.add(formatStructuralError(compId, path + ".args",
                            "'not' function requires 'value' argument"));
                }
            }
            default -> {
                // For other functions (required, email, regex, length, etc.),
                // 'args' presence already verified by caller
            }
        }

        return errors;
    }

    /**
     * Shuts down an executor service gracefully, waiting briefly for tasks to complete.
     */
    private static void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(COMPONENT_VALIDATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private String formatStructuralError(String compId, String path, String message) {
        return String.format(
                "{\"error\":{\"code\":\"STRUCTURAL_VALIDATION_FAILED\"," +
                        "\"componentId\":\"%s\"," +
                        "\"path\":\"%s\"," +
                        "\"message\":\"%s\"}}",
                compId, path, message.replace("\"", "\\\""));
    }

    /**
     * Collects the result of a single component validation future, handling timeout and execution errors.
     */
    private void collectComponentResult(Future<ComponentValidationResult> future, JsonNode compNode,
                                        int compIndex, int lineIndex, String surfaceLabel, List<String> errors) {
        try {
            ComponentValidationResult result = future.get(
                    COMPONENT_VALIDATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!result.schemaErrors().isEmpty()) {
                final String unknownType = unknownComponentTypeError(compNode, surfaceLabel, result.compId());
                if (unknownType != null) {
                    LOG.info("Line {}: component '{}' declares an unknown component type", lineIndex + 1, result.compId());
                    errors.add(unknownType);
                } else {
                    final Set<Error> focused = focusComponentErrors(result.schemaErrors(), compNode);
                    LOG.info("Line {}: component '{}' validate={}ms, schemaErrors={} (reported={})",
                            lineIndex + 1, result.compId(), result.elapsedMs(),
                            result.schemaErrors().size(), focused.size());
                    for (Error error : focused) {
                        errors.add(String.format(
                                "{\"error\":{\"code\":\"VALIDATION_FAILED\"," +
                                        "\"surfaceId\":\"%s\"," +
                                        "\"componentId\":\"%s\"," +
                                        "\"path\":\"%s\"," +
                                        "\"message\":\"%s\"}}",
                                surfaceLabel,
                                result.compId(),
                                error.getEvaluationPath(),
                                error.getMessage().replace("\"", "\\\"")));
                    }
                }
            }
            errors.addAll(result.structuralErrors());
        } catch (TimeoutException e) {
            future.cancel(true);
            String compId = compNode.has("id") ? compNode.get("id").asText() : "index-" + compIndex;
            LOG.error("Line {}: component '{}' validation timed out after {}s",
                    lineIndex + 1, compId, COMPONENT_VALIDATION_TIMEOUT.toSeconds());
            errors.add(String.format(
                    "{\"error\":{\"code\":\"VALIDATION_TIMEOUT\"," +
                            "\"surfaceId\":\"%s\"," +
                            "\"componentId\":\"%s\"," +
                            "\"message\":\"Schema validation timed out\"}}",
                    surfaceLabel, compId));
        } catch (ExecutionException e) {
            LOG.error("Line {}: component validation failed: {}", lineIndex + 1, e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Component validation interrupted");
        }
    }

    /**
     * Reads the catalog's {@code $defs.anyComponent.oneOf} and maps each component name to its
     * branch index, so validation failures can be attributed to the branch the component targets.
     * Returns an empty map if the catalog cannot be read - focusing is then skipped and the raw
     * error set is reported, which is the pre-existing behaviour.
     */
    private static Map<String, Integer> loadComponentBranchIndex(String catalogResource) {
        final String resourcePath = catalogResource.startsWith(CLASSPATH_PREFIX)
                ? catalogResource.substring(CLASSPATH_PREFIX.length())
                : catalogResource;

        try (var is = ValidateA2UIModelCorrectness.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.warn("Could not read catalog '{}' for error focusing; full error sets will be reported", resourcePath);
                return Map.of();
            }

            final JsonNode branches = new ObjectMapper().readTree(is)
                    .path("$defs").path("anyComponent").path("oneOf");
            if (!branches.isArray()) return Map.of();

            final Map<String, Integer> index = new LinkedHashMap<>();
            for (int i = 0; i < branches.size(); i++) {
                // Branch refs are of the form "#/components/<ComponentName>"
                final String ref = branches.get(i).path("$ref").asText("");
                final int lastSlash = ref.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < ref.length() - 1) {
                    index.put(ref.substring(lastSlash + 1), i);
                }
            }
            LOG.info("Loaded {} component branches from '{}' for validation error focusing", index.size(), resourcePath);
            return Map.copyOf(index);
        } catch (Exception e) {
            LOG.warn("Failed to index catalog components for error focusing: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Reduces a component's schema errors to the ones that actually describe what is wrong with it.
     *
     * <p>{@code anyComponent} is a {@code oneOf} over every component type in the catalog, so a
     * single bad property makes the component fail all ~22 branches at once and the validator
     * reports why against each of them. The overwhelming majority of that output is noise about
     * component types the author never intended - "must be the constant value 'Tabs'" and similar.
     * The catalog declares {@code discriminator: {propertyName: "component"}}, so the intended
     * branch is known exactly: keep only that branch's errors.</p>
     *
     * <p>Within the surviving branch, {@code unevaluatedProperties} errors are dropped whenever a
     * concrete failure is also present. They are a cascade: once a subschema fails, its properties
     * count as unevaluated and every field on the component is reported a second time.</p>
     */
    private Set<Error> focusComponentErrors(Set<Error> schemaErrors, JsonNode component) {
        if (schemaErrors.size() <= 1 || componentBranchIndex.isEmpty()) return schemaErrors;

        final String declaredType = component.path("component").asText(null);
        if (declaredType == null || declaredType.isBlank()) return schemaErrors;

        final Integer branch = componentBranchIndex.get(declaredType);
        if (branch == null) return schemaErrors;

        final String branchPrefix = "/oneOf/" + branch + "/";
        final Set<Error> onBranch = schemaErrors.stream()
                .filter(error -> evaluationPathOf(error).startsWith(branchPrefix))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // No branch-specific errors means the failure is structural (e.g. not an object at all)
        if (onBranch.isEmpty()) return schemaErrors;

        final Set<Error> concrete = onBranch.stream()
                .filter(error -> !evaluationPathOf(error).endsWith(UNEVALUATED_PROPERTIES))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return concrete.isEmpty() ? onBranch : concrete;
    }

    /**
     * A component naming a type the catalog does not define fails every branch on the discriminator
     * alone. The full error set says nothing useful; the unknown name does.
     */
    private String unknownComponentTypeError(JsonNode component, String surfaceLabel, String compId) {
        final String declaredType = component.path("component").asText(null);
        if (declaredType == null || declaredType.isBlank() || componentBranchIndex.isEmpty()
                || componentBranchIndex.containsKey(declaredType)) {
            return null;
        }

        return String.format(
                "{\"error\":{\"code\":\"VALIDATION_FAILED\",\"surfaceId\":\"%s\",\"componentId\":\"%s\"," +
                        "\"message\":\"Unknown component type '%s'. Valid component types are: %s\"}}",
                surfaceLabel, compId, declaredType, String.join(", ", componentBranchIndex.keySet()));
    }

    private static String evaluationPathOf(Error error) {
        return String.valueOf(error.getEvaluationPath());
    }

    private List<String> formatErrors(Set<Error> schemaErrors, String surfaceLabel) {
        return schemaErrors.stream()
                .map(error -> String.format(
                        "{\"error\":{\"code\":\"VALIDATION_FAILED\"," +
                                "\"surfaceId\":\"%s\"," +
                                "\"path\":\"%s\"," +
                                "\"message\":\"%s\"}}",
                        surfaceLabel,
                        error.getEvaluationPath(),
                        error.getMessage().replace("\"", "\\\"")))
                .toList();
    }

    public static void main(String[] args) {
        try {
            long t0 = System.nanoTime();
            ValidateA2UIModelCorrectness validator = new ValidateA2UIModelCorrectness();
            LOG.info("Constructor: {}ms", (System.nanoTime() - t0) / 1_000_000);

            long t1 = System.nanoTime();
            String a2uiOutput;
            try (var is = ValidateA2UIModelCorrectness.class.getClassLoader()
                    .getResourceAsStream("a2ui/testModel.jsonl")) {
                if (is == null) {
                    LOG.error("Test model resource 'a2ui/testModel.jsonl' not found on classpath");
                    System.exit(2);
                    return;
                }
                a2uiOutput = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            LOG.info("File read: {}ms, size={} bytes, lines={}",
                    (System.nanoTime() - t1) / 1_000_000,
                    a2uiOutput.length(),
                    a2uiOutput.lines().count());

            long t2 = System.nanoTime();
            List<String> messages = validator.validateA2uiOutput(a2uiOutput);
            LOG.info("Validation: {}ms", (System.nanoTime() - t2) / 1_000_000);

            if (messages.isEmpty()) {
                LOG.info("Validation passed — no issues found.");
            } else {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Validation found {} issue(s):", messages.size());
                    for (int i = 0; i < messages.size(); i++) {
                        LOG.warn("  {}. {}", i + 1, messages.get(i));
                    }
                }
            }

            System.exit(messages.isEmpty() ? 0 : 1);
        } catch (Exception e) {
            LOG.error("Failed to initialise validator: {}", e.getMessage(), e);
            System.exit(2);
        }
    }
}
