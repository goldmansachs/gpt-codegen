package org.rj.modelgen.ui.models.generation.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Result of the UI impact analysis LLM call. Identifies which components in the
 * existing UI are affected by the user's request, or - for initial generation -
 * whether a new UI should be produced at all.
 *
 * <p>Deliberately kept parallel to the BPMN {@code ImpactAnalysisResult} to keep
 * the shape of impact analysis consistent across generation domains, minus the
 * BPMN-specific payload/starting-variable concerns which do not apply to UI.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UIImpactAnalysisResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_EXTRACT = Pattern.compile("^.*?(\\{.*}).*?$", Pattern.DOTALL | Pattern.MULTILINE);
    private static final String FIX_INVALID_ESCAPES = "([^\\\\])\\\\([^\"\\\\/bfnrt])";

    @JsonProperty("affectedComponentIds")
    private List<String> affectedComponentIds = new ArrayList<>();

    @JsonProperty("addComponents")
    private boolean addComponents;

    @JsonProperty("removeComponentIds")
    private List<String> removeComponentIds = new ArrayList<>();

    @JsonProperty("formalAnalysis")
    private String formalAnalysis;

    @JsonProperty("commentary")
    private String commentary;

    public UIImpactAnalysisResult() {
    }

    public UIImpactAnalysisResult(List<String> affectedComponentIds, boolean addComponents,
                                  List<String> removeComponentIds, String formalAnalysis, String commentary) {
        this.affectedComponentIds = affectedComponentIds != null ? affectedComponentIds : new ArrayList<>();
        this.addComponents = addComponents;
        this.removeComponentIds = removeComponentIds != null ? removeComponentIds : new ArrayList<>();
        this.formalAnalysis = formalAnalysis;
        this.commentary = commentary;
    }

    public List<String> getAffectedComponentIds() {
        return affectedComponentIds != null ? affectedComponentIds : List.of();
    }

    public boolean isAddComponents() {
        return addComponents;
    }

    public List<String> getRemoveComponentIds() {
        return removeComponentIds != null ? removeComponentIds : List.of();
    }

    public String getFormalAnalysis() {
        return formalAnalysis;
    }

    public String getCommentary() {
        return commentary;
    }

    public boolean isComponentAffected(String componentId) {
        return getAffectedComponentIds().contains(componentId) || getRemoveComponentIds().contains(componentId);
    }

    public Set<String> allImpactedIds() {
        Set<String> ids = new HashSet<>(getAffectedComponentIds());
        ids.addAll(getRemoveComponentIds());
        return ids;
    }

    /**
     * Whether the analysis concluded that no tangible UI changes are required.
     */
    public boolean isNoChangeRequired() {
        return !addComponents
                && getAffectedComponentIds().isEmpty()
                && getRemoveComponentIds().isEmpty();
    }

    public static String sanitize(String rawContent) {
        if (rawContent == null) return null;

        // Strip control chars (keep whitespace) so downstream JSON parsing doesn't choke on stray output
        String content = rawContent.replaceAll("[\\p{Cntrl}&&[^\n\r\t]]", "");

        final var matcher = JSON_EXTRACT.matcher(content);
        if (matcher.find()) {
            content = matcher.group(1);
        }

        content = content.replaceAll(FIX_INVALID_ESCAPES, "$1$2");
        return content;
    }

    public static UIImpactAnalysisResult fromJson(String json) {
        try {
            return MAPPER.readValue(json, UIImpactAnalysisResult.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse UI impact analysis result: " + e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return String.format("UIImpactAnalysisResult{affected=%s, addComponents=%s, remove=%s, formalAnalysis='%s', commentary='%s'}",
                affectedComponentIds, addComponents, removeComponentIds, formalAnalysis, commentary);
    }
}

