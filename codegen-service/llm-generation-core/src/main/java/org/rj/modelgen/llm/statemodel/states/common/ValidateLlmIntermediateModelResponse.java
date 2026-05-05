package org.rj.modelgen.llm.statemodel.states.common;

import org.rj.modelgen.llm.intrep.core.model.IntermediateModel;
import org.rj.modelgen.llm.models.generation.multilevel.MultiLevelGenerationModel;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.response.ModelResponse;
import org.rj.modelgen.llm.schema.ModelSchema;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.state.ModelInterfaceStateMachine;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.statemodel.signals.common.CommonStateInterface;
import org.rj.modelgen.llm.validation.IntermediateModelValidationProvider;
import org.rj.modelgen.llm.validation.beans.IntermediateModelValidationError;
import org.rj.modelgen.llm.validation.beans.IntermediateModelValidationErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ValidateLlmIntermediateModelResponse
        extends ModelInterfaceState implements CommonStateInterface {

    private static final Logger LOG = LoggerFactory.getLogger(ValidateLlmIntermediateModelResponse.class);

    private final ModelSchema modelSchema;
    private final IntermediateModelValidationProvider<? extends IntermediateModel> validationProvider;
    private String modelInputKey;
    private boolean collectAsInitialValidations = false;
    private boolean filterUsingInitialValidations = false;


    public ValidateLlmIntermediateModelResponse(ModelSchema modelSchema, Class<? extends IntermediateModel> modelClass) {
        this(ValidateLlmIntermediateModelResponse.class, modelSchema, modelClass);
    }

    public ValidateLlmIntermediateModelResponse(Class<? extends ValidateLlmIntermediateModelResponse> cls,
                                                ModelSchema modelSchema, Class<? extends IntermediateModel> modelClass) {
        super(cls);
        this.modelSchema = modelSchema;
        this.validationProvider = new IntermediateModelValidationProvider<>(modelSchema, modelClass);
    }

    @Override
    public String getDescription() {
        return "Validating intermediate representation returned by LLM";
    }

    @Override
    protected Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal input) {

        if (!collectAsInitialValidations) {
            final ModelResponse response = getPayload().get(StandardModelData.ModelResponse);
            if (response != null && !response.isSuccessful()) {
                return error(String.format("LLM model execution ended in failure (%s)", response.getError()));
            }
        }

        // Perform validation
        final String modelContent = getPayload().get(getModelInputKey());
        final var errors = validationProvider.validate(modelContent);

        if (collectAsInitialValidations) {
            final List<IntermediateModelValidationError> initialErrors = errors.hasErrors()
                    ? new ArrayList<>(errors.getErrors())
                    : List.of();

            final String sessionId = getPayload().get(StandardModelData.SessionId);
            LOG.info("Session {} recorded {} initial validation error(s) on reverse-rendered intermediate model",
                    sessionId, initialErrors.size());

            return outboundSignal(getSuccessSignalId())
                    .withPayloadData(MultiLevelModelStandardPayloadData.InitialValidations, initialErrors)
                    .withPayloadData(StandardModelData.ValidationMessages, List.of())
                    .mono();
        }

        IntermediateModelValidationErrors effectiveErrors = errors;
        if (filterUsingInitialValidations) {
            final List<IntermediateModelValidationError> initialValidations = Optional
                    .<List<IntermediateModelValidationError>>ofNullable(
                            getPayload().get(MultiLevelModelStandardPayloadData.InitialValidations.toString()))
                    .orElse(List.of());

            if (!initialValidations.isEmpty() && errors.hasErrors()) {
                final Set<IntermediateModelValidationError> initialSet = new HashSet<>(initialValidations);
                final List<IntermediateModelValidationError> filtered = errors.getErrors().stream()
                        .filter(e -> !initialSet.contains(e))
                        .collect(Collectors.toList());

                final int ignored = errors.getErrors().size() - filtered.size();
                if (ignored > 0) {
                    LOG.info("Ignoring {} validation error(s) which were present in the initial validation set",
                            ignored);
                }

                effectiveErrors = new IntermediateModelValidationErrors(filtered);
            }
        }

        if (effectiveErrors.hasErrors()) {
            LOG.info(String.format("LLM intermediate model response failed validation (%s)", effectiveErrors.getErrors().stream().map(IntermediateModelValidationError::toString).collect(Collectors.joining("; "))));
        } else {
            final String sessionId = getPayload().get(StandardModelData.SessionId);
            LOG.info("Session {} intermediate model response passed validations", sessionId);
        }

        return outboundSignal(getSuccessSignalId())
                .withPayloadData(StandardModelData.ValidationMessages, List.of(effectiveErrors))
                .mono();
    }

    public ValidateLlmIntermediateModelResponse withModelInputKey(String inputKey) {
        setModelInputKey(inputKey);
        return this;
    }
    public<T extends Enum<T>> ValidateLlmIntermediateModelResponse withModelInputKey(T inputKey) {
        setModelInputKey(inputKey.toString());
        return this;
    }

    public void setModelInputKey(String inputKey) {
        this.modelInputKey = inputKey;
    }

    public ValidateLlmIntermediateModelResponse withCollectAsInitialValidations(boolean collect) {
        this.collectAsInitialValidations = collect;
        return this;
    }

    public ValidateLlmIntermediateModelResponse withFilterUsingInitialValidations(boolean filter) {
        this.filterUsingInitialValidations = filter;
        return this;
    }

    // Input key can be explicitly provided to control which input data is validated.  If not provided, this node
    // will validate the last model response received by default
    private String getModelInputKey() {
        if (modelInputKey == null) {
            return StandardModelData.ResponseContent.toString();
        }

        return modelInputKey;
    }
}
