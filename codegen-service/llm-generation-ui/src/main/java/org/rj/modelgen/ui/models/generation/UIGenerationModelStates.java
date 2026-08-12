package org.rj.modelgen.ui.models.generation;

import org.rj.modelgen.llm.util.StringSerializable;

/**
 * Defines the generic states in the UI generation pipeline that are shared
 * across all UI generation model implementations (e.g. A2UI, future formats).
 *
 * <p>These states handle the common preprocessing stages:</p>
 * <ol>
 *   <li><b>StartUIGeneration</b> — Validate input and initialize session context</li>
 *   <li><b>ExecuteImpactAnalysis</b> — LLM call which decides whether generation is required</li>
 *   <li><b>EvaluateImpactAnalysis</b> — Routes to Complete (no changes) or the target flow</li>
 *   <li><b>Complete</b> — Terminal state capturing pipeline outputs</li>
 * </ol>
 *
 * <p>Target-specific states (e.g. ConvertToA2UI, ValidateOutput) are defined by
 * each concrete subclass.</p>
 */
public enum UIGenerationModelStates implements StringSerializable {
    StartUIGeneration,
    ExecuteImpactAnalysis,
    EvaluateImpactAnalysis,
    Complete;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }

    public String description() {
        return switch (this) {
            case StartUIGeneration -> "Starting UI generation pipeline";
            case ExecuteImpactAnalysis -> "Analyzing request to determine which parts of the UI need to change";
            case EvaluateImpactAnalysis -> "Evaluating impact analysis to determine whether generation should proceed";
            case Complete -> "UI generation pipeline complete";
        };
    }
}
