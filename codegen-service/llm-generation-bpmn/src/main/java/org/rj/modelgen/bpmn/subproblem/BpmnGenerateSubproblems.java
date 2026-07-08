package org.rj.modelgen.bpmn.subproblem;

import org.json.JSONObject;
import org.rj.modelgen.bpmn.generation.BpmnConstants;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.models.generation.base.context.BpmnPromptPlaceholders;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.subproblem.data.SubproblemDecomposition;
import org.rj.modelgen.llm.subproblem.states.GenerateSubproblems;
import org.rj.modelgen.llm.util.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.EXPECTED_INLINE_SUBPROCESS_IDS;

public class BpmnGenerateSubproblems extends GenerateSubproblems {

    public BpmnGenerateSubproblems() {
        super(BpmnGenerateSubproblems.class);
    }

    @Override
    protected Result<List<String>, String> decomposeIntoSubproblems(String problem) {
        if (isIrModel()) {
            return decomposeIrModel(BpmnIntermediateModel.fromJson(new JSONObject(problem)));
        }

        final Result<List<String>, String> result = super.decomposeIntoSubproblems(problem);
        if (result.isOk()) {
            recordInlineSubprocessIds(result.getValue());
        }
        return result;
    }

    private void recordInlineSubprocessIds(List<String> subproblems) {
        final List<String> inlineSubprocessIds = new ArrayList<>();
        for (int i = 1; i < subproblems.size(); i++) {   // SP0 (index = 0) is the main process
            final String subProcessId = getPayload().getOrElse(
                    SubproblemDecomposition.getSubproblemToSubprocessDataKey(i), (String) null);
            if (subProcessId == null || subProcessId.isBlank()) continue;
            if (!isEventSubprocessBlock(subproblems.get(i))) {
                inlineSubprocessIds.add(subProcessId);
            }
        }
        getPayload().put(EXPECTED_INLINE_SUBPROCESS_IDS, inlineSubprocessIds);
    }

    // A subprocess block is an event subprocess when it starts with a triggered start-event tag (anything other
    // than a plain [startEvent]); otherwise it is an inline subprocess that must be invoked via a subprocessCallNode.
    private static boolean isEventSubprocessBlock(String content) {
        if (content == null || content.isBlank()) return false;
        final String lower = content.toLowerCase();
        return BpmnConstants.NodeTypes.START_EVENT_TYPES.stream()
                .filter(type -> !BpmnConstants.NodeTypes.START_EVENT.equals(type))
                .anyMatch(type -> lower.contains("[" + type.toLowerCase() + "]"));
    }

    private boolean isIrModel() {
        return MultiLevelModelStandardPayloadData.SerializedReverseRender.toString().equals(getInputKey());
    }

    private Result<List<String>, String> decomposeIrModel(BpmnIntermediateModel model) {
        final List<BpmnIntermediateModel> subModels = model.hasSubModels() ? new ArrayList<>(model.getSubModels()) : List.of();

        // Record the inline subprocess ids the main process is expected to call
        final List<String> inlineSubprocessIds = subModels.stream()
                .filter(sm -> sm.getSubProcessConfig() != null && !sm.isTriggeredByEvent())
                .map(sm -> sm.getSubProcessConfig().getSubProcessId())
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
        getPayload().put(EXPECTED_INLINE_SUBPROCESS_IDS, inlineSubprocessIds);

        model.setSubModels(new ArrayList<>());
        final String mainProcessJson = model.serialize();
        model.setSubModels(subModels);

        final List<String> subproblems = new ArrayList<>();
        subproblems.add(mainProcessJson);

        for (int i = 0; i < subModels.size(); i++) {
            final BpmnIntermediateModel subModel = subModels.get(i);
            final int subproblemIndex = i + 1;

            final String subProcessId = subModel.getSubProcessConfig() != null  ? subModel.getSubProcessConfig().getSubProcessId() : null;
            if (subProcessId != null) {
                getPayload().put(SubproblemDecomposition.getSubproblemToSubprocessDataKey(subproblemIndex), subProcessId);
                getPayload().put(SubproblemDecomposition.getSubprocessNameToSubproblemDataKey(subProcessId), subproblemIndex);
            }

            subproblems.add(subModel.serialize());
        }

        return Result.Ok(subproblems);
    }

    @Override
    protected void onStartingNewSubproblem(int subproblemId, int subproblemCount) {
        getPayload().removeAll(List.of(
                StandardModelData.ResponseContent.toString(),
                StandardModelData.ModelResponse.toString(),
                StandardModelData.ValidationMessages.toString(),
                MultiLevelModelStandardPayloadData.HighLevelModel.toString(),
                MultiLevelModelStandardPayloadData.DetailLevelModel.toString(),
                BpmnPromptPlaceholders.GLOBAL_VARIABLES_USED_IN_HL_MODEL.getValue(),
                BpmnPromptPlaceholders.DETAIL_MODEL_VALIDATION_ISSUES.getValue()
        ));
    }
}
