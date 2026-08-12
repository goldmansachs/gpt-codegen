package org.rj.modelgen.ui.models.generation.a2ui;

import org.rj.modelgen.llm.util.StringSerializable;

/**
 * Defines the A2UI-specific states in the UI generation pipeline.
 * Generic states (Start, ExecuteImpactAnalysis, EvaluateImpactAnalysis, Complete) are defined in
 * {@link org.rj.modelgen.ui.models.generation.UIGenerationModelStates}.
 *
 * <p>These states handle the A2UI-specific processing:</p>
 * <ol>
 *   <li><b>ConvertToA2UI</b> — Transform the structured intent into A2UI (Agent 2 UI) format</li>
 *   <li><b>ValidateOutput</b> — Validate the generated A2UI output against the schema</li>
 * </ol>
 */
public enum A2UIStates implements StringSerializable {
    ConvertToA2UI,
    ValidateOutput;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }

    public String description() {
        return switch (this) {
            case ConvertToA2UI -> "Converting user intent into A2UI model";
            case ValidateOutput -> "Validating the generated A2UI model output";
        };
    }
}
