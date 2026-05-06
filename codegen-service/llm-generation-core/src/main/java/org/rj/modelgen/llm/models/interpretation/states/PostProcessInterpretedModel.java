package org.rj.modelgen.llm.models.interpretation.states;

import org.rj.modelgen.llm.models.interpretation.data.InterpretationPayloadData;
import org.rj.modelgen.llm.statemodel.states.common.ExecuteLogic;
import org.rj.modelgen.llm.util.Result;
import reactor.core.publisher.Mono;

import java.util.Optional;

public class PostProcessInterpretedModel extends ExecuteLogic {

    public PostProcessInterpretedModel() {
        this(PostProcessInterpretedModel.class);
    }

    public PostProcessInterpretedModel(Class<? extends ExecuteLogic> cls) {
        super(cls);
    }

    @Override
    protected Mono<Result<Void, String>> executeLogic() {
        final String modelKey = getModelKey();
        final String interpretationResult = Optional.ofNullable(getPayload().<String>get(modelKey))
                .map(this::trimTrailingNewline)
                .orElse(null);

        if (interpretationResult == null) {
            return Mono.just(Result.Err("Interpretation result is null or missing"));
        }

        final var saveResult = saveInterpretationResult(getModelKey(), interpretationResult);

        if (saveResult.isErr()) {
            return Mono.just(Result.Err("Failed to save interpretation output during render: " + saveResult.getError()));
        }

        return Mono.just(Result.Ok());
    }

    private String getModelKey() {
        return InterpretationPayloadData.InterpretationResult.toString();
    }

    private String trimTrailingNewline(String result) {
        return result.endsWith("\n") ? result.substring(0, result.length() - 1) : result;
    }

    private Result<Void, String> saveInterpretationResult(String modelKey, String updatedResult) {
        getPayload().put(modelKey, updatedResult);
        return Result.Ok();
    }

    @Override
    public String getDescription() {
        return "Postprocess interpreted IR model";
    }
}