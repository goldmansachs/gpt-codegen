package org.rj.modelgen.llm.state;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.rj.modelgen.llm.exception.LlmGenerationModelException;
import org.rj.modelgen.llm.model.ModelInterface;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;


public abstract class ModelInterfaceState {
    private final Class<? extends ModelInterfaceState> stateClass;
    private final ModelInterfaceStateType type;
    private String id;
    private ModelInterfaceStateMachine model;
    private int invokeCount;
    private Integer invokeLimit;
    private ModelInterfacePayload payload = new ModelInterfacePayload();
    private String lastError;

    public ModelInterfaceState(Class<? extends ModelInterfaceState> cls) {
        this(cls, ModelInterfaceStateType.DEFAULT);
    }

    public ModelInterfaceState(Class<? extends ModelInterfaceState> cls, ModelInterfaceStateType type) {
        this.id = defaultStateId(cls);
        this.stateClass = cls;
        this.type = type;

        this.invokeCount = 0;
        this.invokeLimit = null;        // Limit is set if non-null
    }

    public String getId() {
        return id;
    }

    @JsonIgnore
    public void overrideDefaultId(String newStateId) {
        this.id = newStateId;
    }

    @JsonIgnore
    public ModelInterfaceState withOverriddenId(String newStateId) {
        overrideDefaultId(newStateId);
        return this;
    }

    public Class<? extends ModelInterfaceState> getStateClass() {
        return stateClass;
    }

    public ModelInterfaceStateType getType() {
        return type;
    }

    /**
     * Implemented by subclasses; return a text description of the state
     */
    @JsonIgnore
    public abstract String getDescription();

    @JsonIgnore
    public boolean isSameStateType(ModelInterfaceState otherState) {
        if (otherState == null) return false;
        return Objects.equals(id, otherState.id);
    }

    public void registerWithModel(ModelInterfaceStateMachine model) {
        this.model = model;
    }

    protected ModelInterface getModelInterface() {
        return Optional.ofNullable(model).map(ModelInterfaceStateMachine::getModelInterface).orElse(null);
    }

    public int getInvokeCount() {
        return invokeCount;
    }

    public Integer getInvokeLimit() {
        return invokeLimit;
    }

    @JsonIgnore
    public boolean hasInvokeLimit() {
        return invokeLimit != null;
    }

    public void setInvokeLimit(Integer invokeLimit) {
        this.invokeLimit = invokeLimit;
    }

    public boolean isTerminal() {
        return  type == ModelInterfaceStateType.TERMINAL_SUCCESS ||
                type == ModelInterfaceStateType.TERMINAL_FAILURE;
    }

    protected ModelInterfacePayload getPayload() {
        return payload;
    }

    public boolean hasError() {
        return getLastError() != null;
    }

    public String getLastError() {
        return lastError;
    }

    protected void setLastError(String error) {
        this.lastError = error;
    }


    /**
     * Called by the model interface state machine when entering the new state.  Performs some basic operations
     * before delegating to subclasses for all action logic
     *
     * @param inputSignal       Signal received from the previous state
     * @return                  Output signal containing the result of this action
     */
    @JsonIgnore
    public Mono<ModelInterfaceSignal> invoke(ModelInterfaceSignal inputSignal) {
        this.invokeCount += 1;
        if (hasInvokeLimit() && invokeCount > invokeLimit) {
            return outboundSignal(new ModelInterfaceStandardSignals.FAIL_MAX_INVOCATIONS(id, invokeCount))
                    .withPayload(payload)
                    .mono();
        }

        this.payload = inputSignal.getPayload();
        this.lastError = null;  // Reset for each execution

        return invokeAction(inputSignal);
    }

    /**
     * Implemented by subclasses.  Perform all actions attached to this state, based on the input signal
     * received from the previous state, and output a new signal containing the results of this action
     *
     * @param inputSignal       Signal received from the previous state
     * @return                  Output signal containing the result of this action
     */
    @JsonIgnore
    protected abstract Mono<ModelInterfaceSignal> invokeAction(ModelInterfaceSignal inputSignal);

    /**
     * Generates an outbound signal based on the provided data
     *
     * @param signalId          Outbound signal ID
     * @return                  Signal with all required data for the execution model
     */
    protected <E extends Enum<E>> ModelInterfaceSignal outboundSignal(E signalId) {
        if (signalId == null) throw new LlmGenerationModelException("Invalid null outbound signal at state: " + id);
        return outboundSignal(signalId.toString());
    }

    /**
     * Generates an outbound signal based on the provided data
     *
     * @param signalId          Outbound signal ID
     * @return                  Signal with all required data for the execution model
     */
    protected ModelInterfaceSignal outboundSignal(String signalId) {
        if (StringUtils.isBlank(signalId)) throw new LlmGenerationModelException("Invalid null outbound signal at state: " + id);

        ModelInterfaceSignal signal = new ModelInterfaceSignal(signalId);
        return outboundSignal(signal);
    }

    /**
     * Generates an outbound signal based on the provided data
     *
     * @param signal            Outbound signal
     * @return                  Signal with all required data for the execution model
     */
    protected ModelInterfaceSignal outboundSignal(ModelInterfaceSignal signal) {
        if (signal == null) throw new LlmGenerationModelException("Invalid null outbound signal at state: " + id);

        // Transfer all payload data from this state to the outbound signal
        signal.getPayload().putAllIfAbsent(this.getPayload());

        return signal;
    }

    /**
     * Generates an outbound signal indicating model completion
     *
     * @return                  Terminal outbound signal
     */
    protected Mono<ModelInterfaceSignal> terminalSignal() {
        return Mono.empty();
    }

    @JsonIgnore
    public static String defaultStateId(Class<? extends ModelInterfaceState> cls) {
        return cls.getSimpleName();
    }

    @JsonIgnore
    protected Mono<ModelInterfaceSignal> error(String message) {
        return outboundSignal(new ModelInterfaceStandardSignals.GENERAL_ERROR(id, message)).mono();
    }

    @JsonIgnore
    public <TState extends ModelInterfaceState>
    Optional<TState> getAs(Class<TState> cls) {
        if (stateClass == cls) {
            return Optional.of((TState)this);
        }

        return Optional.empty();
    }
}
