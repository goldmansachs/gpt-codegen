package org.rj.modelgen.ui.models.generation.a2ui;

import org.rj.modelgen.llm.util.StringSerializable;

/**
 * Defines the A2UI-specific states in the UI generation pipeline.
 * Generic states (Start, ExecuteImpactAnalysis, EvaluateImpactAnalysis, Complete) are defined in
 * {@link org.rj.modelgen.ui.models.generation.UIGenerationModelStates}.
 *
 * <p>These states handle the A2UI-specific processing:</p>
 * <ol>
 *   <li><b>BuildScopedContext</b> — Derive the scoped-generation masking instructions from the
 *       impact analysis (copilot flows that opt in)</li>
 *   <li><b>ConvertToA2UI</b> — Transform the structured intent into A2UI (Agent 2 UI) format</li>
 *   <li><b>MergeScoped</b> — Merge a scoped LLM response back into the full model</li>
 *   <li><b>ValidateOutput</b> — Validate the generated A2UI output against the schema</li>
 * </ol>
 *
 * <p>Declaring an id here does not wire anything: the scoped states are added to the pipeline
 * by whichever consumer opts in, via its {@code UIGenerationTargetConfig}.</p>
 */
public enum A2UIStates implements StringSerializable {
    BuildScopedContext,
    ConvertToA2UI,
    MergeScoped,
    ValidateOutput;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }

    public String description() {
        return switch (this) {
            case BuildScopedContext -> "Determining which components are in scope for this change";
            case ConvertToA2UI -> "Converting user intent into A2UI model";
            case MergeScoped -> "Merging the scoped changes back into the full A2UI model";
            case ValidateOutput -> "Validating the generated A2UI model output";
        };
    }
}
