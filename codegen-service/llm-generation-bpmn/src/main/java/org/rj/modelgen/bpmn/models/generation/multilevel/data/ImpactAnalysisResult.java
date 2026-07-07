package org.rj.modelgen.bpmn.models.generation.multilevel.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Result of the copilot impact analysis LLM call.
 * Identifies which nodes in the existing model are affected by a user's change request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImpactAnalysisResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern JSON_EXTRACT = Pattern.compile("^.*?(\\{.*}).*?$", Pattern.DOTALL | Pattern.MULTILINE);
    private static final String FIX_INVALID_ESCAPES = "([^\\\\])\\\\([^\"\\\\/bfnrt])";

    @JsonProperty("affectedNodeIds")
    private List<String> affectedNodeIds = new ArrayList<>();

    @JsonProperty("addNodes")
    private boolean addNodes;

    @JsonProperty("removeNodeIds")
    private List<String> removeNodeIds = new ArrayList<>();

    @JsonProperty("payloadChangeRequired")
    private boolean payloadChangeRequired;

    @JsonProperty("reasoning")
    private String reasoning;

    public ImpactAnalysisResult() {
    }

    public ImpactAnalysisResult(List<String> affectedNodeIds, boolean addNodes, List<String> removeNodeIds, String reasoning) {
        this(affectedNodeIds, addNodes, removeNodeIds, false, reasoning);
    }

    public ImpactAnalysisResult(List<String> affectedNodeIds, boolean addNodes, List<String> removeNodeIds, boolean payloadChangeRequired, String reasoning) {
        this.affectedNodeIds = affectedNodeIds != null ? affectedNodeIds : new ArrayList<>();
        this.addNodes = addNodes;
        this.removeNodeIds = removeNodeIds != null ? removeNodeIds : new ArrayList<>();
        this.payloadChangeRequired = payloadChangeRequired;
        this.reasoning = reasoning;
    }

    public List<String> getAffectedNodeIds() {
        return affectedNodeIds != null ? affectedNodeIds : List.of();
    }

    public boolean isAddNodes() {
        return addNodes;
    }

    public List<String> getRemoveNodeIds() {
        return removeNodeIds != null ? removeNodeIds : List.of();
    }

    public String getReasoning() {
        return reasoning;
    }

    public boolean isPayloadChangeRequired() {
        return payloadChangeRequired;
    }

    public boolean isNodeAffected(String nodeId) {
        return getAffectedNodeIds().contains(nodeId) || getRemoveNodeIds().contains(nodeId);
    }

    public Set<String> allImpactedIds() {
        Set<String> ids = new HashSet<>(getAffectedNodeIds());
        ids.addAll(getRemoveNodeIds());
        return ids;
    }

    public static String sanitize(String rawContent) {
        if (rawContent == null) return null;

        // Remove invalid control chars, preserving standard whitespace
        String content = rawContent.replaceAll("[\\p{Cntrl}&&[^\n\r\t]]", "");

        // Extract the outermost JSON object from any surrounding text or markdown fences
        final var matcher = JSON_EXTRACT.matcher(content);
        if (matcher.find()) {
            content = matcher.group(1);
        }

        content = content.replaceAll(FIX_INVALID_ESCAPES, "$1$2");

        return content;
    }

    public static ImpactAnalysisResult fromJson(String json) {
        try {
            return MAPPER.readValue(json, ImpactAnalysisResult.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse impact analysis result: " + e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return String.format("ImpactAnalysisResult{affected=%s, addNodes=%s, remove=%s, payloadChangeRequired=%s, reasoning='%s'}",
                affectedNodeIds, addNodes, removeNodeIds, payloadChangeRequired, reasoning);
    }
}
