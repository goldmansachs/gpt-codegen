package org.rj.modelgen.ui.models.generation.states;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.rj.modelgen.llm.state.ModelInterfacePayload;
import org.rj.modelgen.llm.statemodel.states.common.ExecuteLogic;
import org.rj.modelgen.llm.util.Result;
import org.rj.modelgen.llm.util.Util;
import org.rj.modelgen.ui.models.generation.data.UIGenerationModelInputPayload;
import org.rj.modelgen.ui.models.generation.data.UIImpactAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges a scoped LLM response back into the full A2UI model.
 *
 * <p>During scoped generation the LLM is instructed to return only the components the
 * impact analysis flagged. This state takes that partial {@code updateComponents} message
 * and reconciles it against the original model: unaffected components are restored
 * untouched, flagged components take the generated version, brand new components are
 * appended, and removed components are stripped along with any references to them.</p>
 *
 * <p><b>This is where the accidental-edit protection lives.</b> Any component the LLM
 * returns that was not in the allowed set - and is not new - is discarded in favour of the
 * original, which is what makes it safe to show the model the whole form.</p>
 *
 * <p>Mirrors {@code MergeScopedBpmnDetailLevelModel}. No-ops when no scoping data is
 * present, so it is safe on a path shared with unscoped generation.</p>
 */
public class MergeScopedA2UI extends ExecuteLogic {

    private static final Logger LOG = LoggerFactory.getLogger(MergeScopedA2UI.class);
    private static final ObjectMapper MAPPER = Util.getObjectMapper();

    protected static final String ROOT_ID = "root";

    private static final String FIELD_UPDATE_COMPONENTS = "updateComponents";
    private static final String FIELD_COMPONENTS = "components";
    private static final String FIELD_SURFACE_ID = "surfaceId";
    private static final String FIELD_ID = "id";
    private static final String FIELD_CHILD = "child";
    private static final String FIELD_CHILDREN = "children";
    private static final String FIELD_COMPONENT_ID = "componentId";
    private static final String FIELD_TABS = "tabs";
    private static final String FIELD_TRIGGER = "trigger";
    private static final String FIELD_CONTENT = "content";

    /** Single-valued fields whose text content is a component id reference. */
    private static final List<String> SINGLE_CHILD_REF_FIELDS = List.of(FIELD_CHILD, FIELD_TRIGGER, FIELD_CONTENT);

    /**
     * Behaviour modifier keys whose {@code args.rules[].checks[].input} entries reference
     * component ids. Matches the set validated by {@link ValidateA2UIModelCorrectness}.
     */
    private static final List<String> BEHAVIOUR_MODIFIERS = List.of("visibility", "componentRequired", "disabled");

    public MergeScopedA2UI() {
        super(MergeScopedA2UI.class);
    }

    protected MergeScopedA2UI(Class<? extends MergeScopedA2UI> cls) {
        super(cls);
    }

    @Override
    public String getDescription() {
        return "Merge the scoped A2UI response back into the full model";
    }

    @Override
    protected Mono<Result<Void, String>> executeLogic() {
        final var payload = getPayload();

        final String masking = payload.getOrElse(UIGenerationModelInputPayload.SCOPED_A2UI_MASKING_INSTRUCTIONS, (String) null);
        if (masking == null || masking.isBlank()) {
            LOG.debug("No scoped generation context - passing generated output through unchanged");
            return Mono.just(Result.Ok());
        }

        final String originalJsonl = payload.getOrElse(UIGenerationModelInputPayload.ORIGINAL_A2UI_JSONL, (String) null);
        final String generatedJsonl = payload.getOrElse(UIGenerationModelInputPayload.UI_OUTPUT, (String) null);

        if (isBlank(originalJsonl) || isBlank(generatedJsonl)) {
            LOG.warn("Scoped merge skipped - original model is {} and generated model is {}",
                    isBlank(originalJsonl) ? "missing" : "present",
                    isBlank(generatedJsonl) ? "missing" : "present");
            clearScopingData(payload);
            return Mono.just(Result.Ok());
        }

        try {
            return Mono.just(merge(payload, originalJsonl, generatedJsonl));
        } catch (Exception e) {
            // A merge failure must not sink the pipeline: fall back to the original model,
            // which is a valid full form, and let validation proceed against it.
            LOG.warn("Scoped merge failed; falling back to the original model", e);
            payload.put(UIGenerationModelInputPayload.UI_OUTPUT, originalJsonl);
            clearScopingData(payload);
            return Mono.just(Result.Ok());
        }
    }

    private Result<Void, String> merge(ModelInterfacePayload payload, String originalJsonl, String generatedJsonl) throws Exception {
        final List<ObjectNode> originalMessages = parseJsonl(originalJsonl);
        final List<ObjectNode> generatedMessages = parseJsonl(generatedJsonl);

        final List<ObjectNode> originalComponents = collectComponents(originalMessages);
        final List<ObjectNode> generatedComponents = collectComponents(generatedMessages);

        if (findUpdateComponentsMessage(originalMessages) == null) {
            LOG.warn("Original A2UI contains no updateComponents message - skipping scoped merge");
            clearScopingData(payload);
            return Result.Ok();
        }

        final Map<String, ObjectNode> originalById = indexById(originalComponents);
        final Map<String, ObjectNode> generatedById = indexById(generatedComponents);

        if (generatedById.isEmpty()) {
            LOG.warn("LLM returned no components during scoped generation - keeping the original model unchanged");
            payload.put(UIGenerationModelInputPayload.UI_OUTPUT, originalJsonl);
            clearScopingData(payload);
            return Result.Ok();
        }

        final Set<String> allowedIds = idSet(payload, UIGenerationModelInputPayload.SCOPED_A2UI_COMPONENT_IDS);
        final Set<String> removeIds = idSet(payload, UIGenerationModelInputPayload.SCOPED_A2UI_REMOVE_IDS);

        applyImplicitRemovals(payload, generatedById, removeIds);

        if (removeIds.remove(ROOT_ID)) {
            LOG.warn("Impact analysis asked to remove the 'root' component - ignoring, root is never removable");
        }

        final List<ObjectNode> merged = mergeComponents(originalComponents, generatedById, allowedIds, removeIds);

        final Set<String> survivingIds = merged.stream()
                .map(MergeScopedA2UI::idOf)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        final int danglingRemoved = sanitizeReferences(merged, survivingIds);
        if (danglingRemoved > 0) {
            LOG.warn("Removed {} dangling reference(s) to components no longer in the model", danglingRemoved);
        }

        reparentNewOrphans(merged, originalComponents);

        final String surfaceId = resolveSurfaceId(originalMessages);
        postMergeHook(merged, new MergeContext(originalById, removeIds, allowedIds, surfaceId));

        final String mergedJsonl = renderJsonl(originalMessages, merged);

        payload.put(UIGenerationModelInputPayload.UI_OUTPUT, mergedJsonl);
        // Refresh the merge base so a subsequent retry sees the current state of the model
        payload.put(UIGenerationModelInputPayload.ORIGINAL_A2UI_JSONL, mergedJsonl);
        recordAudit("scoped-merge", mergedJsonl);

        LOG.info("Scoped merge complete: {} original, {} returned by LLM, {} removed. Final: {} components.",
                originalComponents.size(), generatedComponents.size(), removeIds.size(), merged.size());

        clearScopingData(payload);
        return Result.Ok();
    }

    /**
     * Any component the analysis flagged as affected but the LLM did not return is treated as
     * implicitly removed or replaced, matching the BPMN merge behaviour.
     */
    private void applyImplicitRemovals(ModelInterfacePayload payload, Map<String, ObjectNode> generatedById, Set<String> removeIds) {
        final UIImpactAnalysisResult impact = EvaluateUIImpactAnalysis.getImpactAnalysis(payload);
        if (impact == null) return;

        for (String affectedId : impact.getAffectedComponentIds()) {
            if (!generatedById.containsKey(affectedId)) {
                removeIds.add(affectedId);
                LOG.info("Affected component '{}' not returned by the LLM - treating as implicitly removed/replaced", affectedId);
            }
        }
    }

    private List<ObjectNode> mergeComponents(List<ObjectNode> originalComponents,
                                             Map<String, ObjectNode> generatedById,
                                             Set<String> allowedIds,
                                             Set<String> removeIds) {
        final List<ObjectNode> merged = new ArrayList<>();
        final Set<String> placed = new LinkedHashSet<>();

        for (ObjectNode original : originalComponents) {
            final String id = idOf(original);
            if (id == null) {
                merged.add(original);
                continue;
            }
            if (removeIds.contains(id)) continue;

            final ObjectNode generated = generatedById.get(id);
            if (generated != null && !allowedIds.contains(id)) {
                // Out-of-scope edit: the LLM changed something the analysis did not flag.
                LOG.warn("Discarding out-of-scope change to component '{}' - not in the affected set", id);
                merged.add(original);
            } else {
                merged.add(generated != null ? generated : original);
            }
            placed.add(id);
        }

        // Anything the LLM returned that did not exist before is a new component
        for (ObjectNode generated : generatedById.values()) {
            final String id = idOf(generated);
            if (id == null || placed.contains(id) || removeIds.contains(id)) continue;
            merged.add(generated);
            placed.add(id);
            LOG.info("Added new component '{}'", id);
        }

        return merged;
    }

    /**
     * Re-parents components that this merge orphaned. Components that were already
     * unreferenced in the original are left alone - non-visual components (Forms'
     * {@code formConfig}, for example) legitimately sit outside the tree.
     */
    private void reparentNewOrphans(List<ObjectNode> merged, List<ObjectNode> originalComponents) {
        final Set<String> originalOrphans = orphanIds(originalComponents);
        final Set<String> mergedOrphans = orphanIds(merged);
        mergedOrphans.removeAll(originalOrphans);
        if (mergedOrphans.isEmpty()) return;

        final ObjectNode root = merged.stream()
                .filter(c -> ROOT_ID.equals(idOf(c)))
                .findFirst()
                .orElse(null);

        if (root == null || !root.path(FIELD_CHILDREN).isArray()) {
            LOG.warn("Components {} were orphaned by the merge but root has no children array to attach them to", mergedOrphans);
            return;
        }

        final ArrayNode children = (ArrayNode) root.get(FIELD_CHILDREN);
        mergedOrphans.forEach(children::add);
        LOG.warn("Attached {} newly orphaned component(s) to root: {}", mergedOrphans.size(), mergedOrphans);
    }

    private Set<String> orphanIds(List<ObjectNode> components) {
        final Set<String> referenced = new LinkedHashSet<>();
        components.forEach(c -> collectChildReferences(c, referenced));

        final Set<String> orphans = new LinkedHashSet<>();
        for (ObjectNode component : components) {
            final String id = idOf(component);
            if (id != null && !ROOT_ID.equals(id) && !referenced.contains(id)) {
                orphans.add(id);
            }
        }
        return orphans;
    }

    // ------------------------------------------------------------------
    //  Reference collection and sanitisation
    // ------------------------------------------------------------------

    private void collectChildReferences(JsonNode component, Set<String> refs) {
        for (String field : SINGLE_CHILD_REF_FIELDS) {
            if (component.path(field).isTextual()) refs.add(component.get(field).asText());
        }

        final JsonNode children = component.path(FIELD_CHILDREN);
        if (children.isArray()) {
            children.forEach(child -> {
                if (child.isTextual()) refs.add(child.asText());
            });
        } else if (children.isObject() && children.path(FIELD_COMPONENT_ID).isTextual()) {
            refs.add(children.get(FIELD_COMPONENT_ID).asText());
        }

        if (component.path(FIELD_TABS).isArray()) {
            for (JsonNode tab : component.get(FIELD_TABS)) {
                if (tab.path(FIELD_CHILD).isTextual()) refs.add(tab.get(FIELD_CHILD).asText());
            }
        }
    }

    private int sanitizeReferences(List<ObjectNode> components, Set<String> survivingIds) {
        int removed = 0;
        for (ObjectNode component : components) {
            removed += sanitizeChildReferences(component, survivingIds);
            removed += sanitizeBehaviourModifiers(component, survivingIds);
        }
        return removed;
    }

    private int sanitizeChildReferences(ObjectNode component, Set<String> survivingIds) {
        int removed = 0;

        for (String field : SINGLE_CHILD_REF_FIELDS) {
            if (component.path(field).isTextual() && !survivingIds.contains(component.get(field).asText())) {
                component.remove(field);
                removed++;
            }
        }

        final JsonNode children = component.path(FIELD_CHILDREN);
        if (children.isArray()) {
            final Iterator<JsonNode> it = children.iterator();
            while (it.hasNext()) {
                final JsonNode child = it.next();
                if (child.isTextual() && !survivingIds.contains(child.asText())) {
                    it.remove();
                    removed++;
                }
            }
        }

        if (component.path(FIELD_TABS).isArray()) {
            final Iterator<JsonNode> it = component.get(FIELD_TABS).iterator();
            while (it.hasNext()) {
                final JsonNode tab = it.next();
                if (tab.path(FIELD_CHILD).isTextual() && !survivingIds.contains(tab.get(FIELD_CHILD).asText())) {
                    it.remove();
                    removed++;
                }
            }
        }

        return removed;
    }

    /**
     * Drops behaviour modifier checks that reference components which no longer exist,
     * pruning rules and the modifier itself when they are left empty. Leaving them would
     * fail {@link ValidateA2UIModelCorrectness}.
     */
    private int sanitizeBehaviourModifiers(ObjectNode component, Set<String> survivingIds) {
        int removed = 0;

        for (String modifier : BEHAVIOUR_MODIFIERS) {
            final JsonNode rules = component.path(modifier).path("args").path("rules");
            if (!rules.isArray()) continue;

            final Iterator<JsonNode> ruleIt = rules.iterator();
            while (ruleIt.hasNext()) {
                final JsonNode rule = ruleIt.next();
                final JsonNode checks = rule.path("checks");
                if (!checks.isArray()) continue;

                final Iterator<JsonNode> checkIt = checks.iterator();
                while (checkIt.hasNext()) {
                    final JsonNode check = checkIt.next();
                    final String input = check.path("input").asText(null);
                    if (input != null && !input.isBlank() && !survivingIds.contains(input)) {
                        checkIt.remove();
                        removed++;
                    }
                }

                if (checks.isEmpty()) ruleIt.remove();
            }

            if (rules.isEmpty()) component.remove(modifier);
        }

        return removed;
    }

    // ------------------------------------------------------------------
    //  JSONL handling
    // ------------------------------------------------------------------

    private List<ObjectNode> parseJsonl(String jsonl) throws Exception {
        final List<ObjectNode> messages = new ArrayList<>();
        for (String line : jsonl.strip().lines().toList()) {
            final String trimmed = line.strip();
            if (trimmed.isEmpty()) continue;
            final JsonNode node = MAPPER.readTree(trimmed);
            if (node instanceof ObjectNode objectNode) messages.add(objectNode);
        }
        return messages;
    }

    private List<ObjectNode> collectComponents(List<ObjectNode> messages) {
        final List<ObjectNode> components = new ArrayList<>();
        for (ObjectNode message : messages) {
            final JsonNode array = message.path(FIELD_UPDATE_COMPONENTS).path(FIELD_COMPONENTS);
            if (!array.isArray()) continue;
            for (JsonNode component : array) {
                if (component instanceof ObjectNode objectNode) components.add(objectNode);
            }
        }
        return components;
    }

    private ObjectNode findUpdateComponentsMessage(List<ObjectNode> messages) {
        return messages.stream()
                .filter(m -> m.path(FIELD_UPDATE_COMPONENTS).path(FIELD_COMPONENTS).isArray())
                .findFirst()
                .orElse(null);
    }

    private String resolveSurfaceId(List<ObjectNode> originalMessages) {
        final ObjectNode updateComponents = findUpdateComponentsMessage(originalMessages);
        return updateComponents == null ? null : updateComponents.path(FIELD_UPDATE_COMPONENTS).path(FIELD_SURFACE_ID).asText(null);
    }

    /**
     * Rebuilds the JSONL from the original messages, replacing the components of the first
     * {@code updateComponents} message with the merged set. Non-component messages
     * ({@code createSurface}, {@code updateDataModel}, ...) are taken from the original.
     */
    private String renderJsonl(List<ObjectNode> originalMessages, List<ObjectNode> merged) throws Exception {
        final ObjectNode carrier = findUpdateComponentsMessage(originalMessages);
        final ArrayNode components = MAPPER.createArrayNode();
        merged.forEach(components::add);
        ((ObjectNode) carrier.get(FIELD_UPDATE_COMPONENTS)).set(FIELD_COMPONENTS, components);

        final StringBuilder sb = new StringBuilder();
        boolean carrierWritten = false;
        for (ObjectNode message : originalMessages) {
            final boolean isUpdateComponents = message.path(FIELD_UPDATE_COMPONENTS).path(FIELD_COMPONENTS).isArray();
            if (isUpdateComponents) {
                if (carrierWritten) {
                    LOG.warn("Dropping additional updateComponents message - all components were merged into the first");
                    continue;
                }
                carrierWritten = true;
            }
            sb.append(MAPPER.writeValueAsString(message)).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    //  Extension point
    // ------------------------------------------------------------------

    /**
     * Extension point for target-specific guardrails, invoked once the generic merge is
     * complete and before the model is serialized. The default implementation does nothing.
     *
     * @param mergedComponents the merged component list, mutable and in emission order
     * @param context          the original components and the id sets used for this merge
     */
    protected void postMergeHook(List<ObjectNode> mergedComponents, MergeContext context) {
        // No-op by default
    }

    /**
     * Context handed to {@link #postMergeHook}.
     *
     * @param originalComponentsById the pre-merge components, keyed by id
     * @param removedIds             ids dropped by this merge (explicit and implicit)
     * @param allowedIds             ids the LLM was permitted to modify
     * @param surfaceId              the A2UI surface being updated, if known
     */
    public record MergeContext(Map<String, ObjectNode> originalComponentsById,
                               Set<String> removedIds,
                               Set<String> allowedIds,
                               String surfaceId) {}

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    protected static String idOf(JsonNode component) {
        return component.path(FIELD_ID).isTextual() ? component.get(FIELD_ID).asText() : null;
    }

    private static Map<String, ObjectNode> indexById(List<ObjectNode> components) {
        final Map<String, ObjectNode> byId = new LinkedHashMap<>();
        for (ObjectNode component : components) {
            final String id = idOf(component);
            if (id != null) byId.put(id, component);
        }
        return byId;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> idSet(ModelInterfacePayload payload, String key) {
        final Object raw = payload.getOrElse(key, (Object) null);
        return raw instanceof Set ? new LinkedHashSet<>((Set<String>) raw) : new LinkedHashSet<>();
    }

    private static void clearScopingData(ModelInterfacePayload payload) {
        // Deliberately leaves IMPACT_ANALYSIS, FORMAL_ANALYSIS and COMMENTARY in place:
        // the conversion prompt still needs the formal analysis on a retry, and the
        // commentary stage consumes its key downstream. Clearing the masking instructions
        // is what makes any subsequent validation retry run unscoped.
        payload.remove(UIGenerationModelInputPayload.SCOPED_A2UI_MASKING_INSTRUCTIONS);
        payload.remove(UIGenerationModelInputPayload.SCOPED_A2UI_COMPONENT_IDS);
        payload.remove(UIGenerationModelInputPayload.SCOPED_A2UI_REMOVE_IDS);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
