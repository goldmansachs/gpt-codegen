package org.rj.modelgen.ui.models.generation;

import org.rj.modelgen.llm.util.StringSerializable;

/**
 * Defines the generic states in the UI generation pipeline that are shared
 * across all UI generation model implementations (e.g. A2UI, future formats).
 *
 * <p>These states handle the common preprocessing stages:</p>
 * <ol>
 *   <li><b>StartUIGeneration</b> — Validate input and initialize session context</li>
 *   <li><b>SanitizeInput</b> — Correct spelling, grammar, and unclear phrasing</li>
 *   <li><b>FormaliseIntent</b> — Transform sanitized request into structured UI element description</li>
 *   <li><b>Complete</b> — Terminal state capturing pipeline outputs</li>
 * </ol>
 *
 * <p>Target-specific states (e.g. ConvertToA2UI, ValidateOutput) are defined by
 * each concrete subclass.</p>
 */
public enum UIGenerationModelStates implements StringSerializable {
    StartUIGeneration,
    SanitizeInput,
    FormaliseIntent,
    Complete;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }

    public String description() {
        return switch (this) {
            case StartUIGeneration -> "Starting UI generation pipeline";
            case SanitizeInput -> "Sanitizing user input";
            case FormaliseIntent -> "Formalizing user intent into structured UI elements";
            case Complete -> "UI generation pipeline complete";
        };
    }
}

