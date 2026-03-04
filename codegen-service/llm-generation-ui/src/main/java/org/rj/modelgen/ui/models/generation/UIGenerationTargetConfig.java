package org.rj.modelgen.ui.models.generation;

import org.rj.modelgen.llm.state.ModelInterfaceState;
import org.rj.modelgen.llm.state.ModelInterfaceTransitionRule;
import org.rj.modelgen.ui.models.generation.states.UIGenerationComplete;

import java.util.List;
import java.util.Objects;

/**
 * Configuration provided by a concrete UI generation model subclass to supply
 * target-specific states and transition rules that are appended to the generic
 * UI generation pipeline (sanitize → formalise intent → [target-specific stages] → complete).
 *
 * <p>Each target format (e.g. A2UI) implements its own conversion and validation
 * states, then packages them into this config so the base {@link UIGenerationModel}
 * can wire them into the full pipeline.</p>
 *
 * <p>Transition rules whose target is a {@link UIGenerationComplete} instance will be
 * automatically resolved to the shared terminal state created by the base pipeline
 * via {@link #resolveTargetRules(ModelInterfaceState)}. Subclasses can therefore
 * use {@code new UIGenerationComplete()} as a placeholder target in their rules
 * without needing access to the actual terminal state.</p>
 */
public class UIGenerationTargetConfig {

    private final List<ModelInterfaceState> targetStates;
    private final List<ModelInterfaceTransitionRule> targetRules;

    /**
     * Creates a new target configuration.
     *
     * @param targetStates  the target-specific states to add to the pipeline (e.g. ConvertToA2UI, ValidateOutput).
     *                      The first state in this list will be wired as the successor of the FormaliseIntent stage.
     *                      Must be non-null and non-empty.
     * @param targetRules   the target-specific transition rules governing flow between the target states
     *                      and from the last target state to the Complete state. Rules that target a
     *                      {@link UIGenerationComplete} instance will be resolved to the shared terminal
     *                      state by the base pipeline.
     */
    public UIGenerationTargetConfig(List<ModelInterfaceState> targetStates,
                                    List<ModelInterfaceTransitionRule> targetRules) {
        Objects.requireNonNull(targetStates, "targetStates must not be null");
        Objects.requireNonNull(targetRules, "targetRules must not be null");
        if (targetStates.isEmpty()) {
            throw new IllegalArgumentException("targetStates must contain at least one state");
        }
        this.targetStates = targetStates;
        this.targetRules = targetRules;
    }

    public List<ModelInterfaceState> getTargetStates() {
        return targetStates;
    }

    public List<ModelInterfaceTransitionRule> getTargetRules() {
        return targetRules;
    }

    /**
     * Returns a copy of the target rules with any {@link UIGenerationComplete} placeholder
     * targets resolved to the given terminal state instance. This allows subclasses to use
     * {@code new UIGenerationComplete()} as a sentinel in their rules without needing access
     * to the actual terminal state created by the base pipeline.
     *
     * @param terminalState the shared terminal state from the base pipeline
     * @return              resolved transition rules with placeholders replaced
     */
    public List<ModelInterfaceTransitionRule> resolveTargetRules(ModelInterfaceState terminalState) {
        return targetRules.stream()
                .map(rule -> {
                    if (rule.getNextState() instanceof UIGenerationComplete) {
                        return new ModelInterfaceTransitionRule(
                                rule.getCurrentState(), rule.getOutputSignalId(), terminalState);
                    }
                    return rule;
                })
                .toList();
    }
}
