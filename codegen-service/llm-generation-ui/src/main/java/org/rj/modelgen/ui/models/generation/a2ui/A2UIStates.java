package org.rj.modelgen.ui.models.generation.pipeline;

import org.rj.modelgen.llm.util.StringSerializable;

/**
 * Defines the states in the A2UI generation pipeline.
 *
 * The pipeline follows three key stages:
 * 1. Sanitize - Correct spelling and grammar in user input without changing intent
 * 2. Formalise Intent - Structure the user's intent into a clear description of UI elements,
 *    including any elements that can be confidently inferred
 * 3. Convert to A2UI - Transform the structured intent into A2UI (Agent 2 UI) format
 */
public enum A2UIPipelineStates implements StringSerializable {
    StartUIGeneration,
    SanitizeInput,
    FormaliseIntent,
    ConvertToA2UI,
    ValidateOutput,
    Complete;

    @Override
    public String toString() {
        return Character.toLowerCase(name().charAt(0)) + name().substring(1);
    }

    public String description() {
        return switch (this) {
            case StartUIGeneration -> "Starting UI generation pipeline";
            case SanitizeInput -> "Sanitizing user input (correcting spelling and grammar)";
            case FormaliseIntent -> "Formalising user intent into structured UI elements";
            case ConvertToA2UI -> "Converting structured intent into A2UI format";
            case ValidateOutput -> "Validating the generated A2UI output";
            case Complete -> "UI generation pipeline complete";
        };
    }
}

