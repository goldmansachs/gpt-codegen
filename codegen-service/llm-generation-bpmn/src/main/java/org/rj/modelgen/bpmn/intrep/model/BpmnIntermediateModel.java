package org.rj.modelgen.bpmn.intrep.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.json.JSONObject;
import org.rj.modelgen.llm.intrep.graph.IntermediateGraphModel;
import org.rj.modelgen.llm.util.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.*;
import static org.rj.modelgen.llm.util.Util.deserializeOrThrow;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpmnIntermediateModel extends IntermediateGraphModel<String, String, ElementConnection, ElementNode> {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<BpmnIntermediateModel> subModels = new ArrayList<>();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private SubProcessConfig subProcessConfig;

    public BpmnIntermediateModel() {
        super();
    }

    public List<BpmnIntermediateModel> getSubModels() {
        return subModels;
    }

    public void setSubModels(List<BpmnIntermediateModel> subModels) {
        this.subModels = subModels;
    }

    public SubProcessConfig getSubProcessConfig() {
        return subProcessConfig;
    }

    public void setSubProcessConfig(SubProcessConfig subProcessConfig) {
        this.subProcessConfig = subProcessConfig;
    }

    @JsonIgnore
    public SubProcessConfig getOrCreateSubProcessConfig() {
        if (subProcessConfig == null) {
            subProcessConfig = new SubProcessConfig();
        }
        return subProcessConfig;
    }

    @JsonIgnore
    public boolean isTriggeredByEvent() {
        return subProcessConfig != null && subProcessConfig.isTriggeredByEvent();
    }

    @JsonIgnore
    public boolean hasSubModels() {
        return subModels != null && !subModels.isEmpty();
    }

    @JsonIgnore
    @Override
    public Stream<ElementNode> getAllNodesRecursive() {
        return Stream.concat(
                getNodes().stream(),
                subModels.stream().flatMap(sm -> sm.getNodes().stream())
        );
    }

    public static BpmnIntermediateModel fromJson(JSONObject json) {
        final var model = deserializeOrThrow(json.toString(), BpmnIntermediateModel.class);
        if (model.getSubModels() == null) {
            model.setSubModels(new ArrayList<>());
        }
        return model;
    }

    public JSONObject toJson() {
        return new JSONObject(serialize());
    }

    public Result<Void, String> validateDependencies() {
        // Validate that all sub-process calls have a matching sub-model definition.
        final var subModelIds = subModels.stream()
                .map(BpmnIntermediateModel::getSubProcessConfig)
                .filter(Objects::nonNull)
                .map(SubProcessConfig::getSubProcessId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final var invalidSubprocessCall = getNodes().stream()
                .filter(node -> node.getElementType().equals(SUBPROCESS))
                .filter(node -> node.getConnectedTo() != null && !node.getConnectedTo().isEmpty())
                .filter(node -> {
                    String spId = node.findInput(SUBPROCESS_ID)
                            .map(ElementNodeInput::getValue)
                            .orElse(null);
                    return spId != null && !subModelIds.contains(spId);
                })
                .findFirst();

        if (invalidSubprocessCall.isPresent()) {
            ElementNode node = invalidSubprocessCall.get();
            String spId = node.findInput(SUBPROCESS_ID)
                    .map(ElementNodeInput::getValue)
                    .orElse(node.getId());
            return Result.Err("Subprocess call node '%s' references subProcessId '%s' which has no corresponding sub-model definition".formatted(node.getName(), spId));
        }

        return Result.Ok();
    }
}
