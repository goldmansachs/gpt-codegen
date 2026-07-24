package org.rj.modelgen.ui.models.generation;

import org.rj.modelgen.llm.prompt.TemplatedPromptGenerator;
import org.rj.modelgen.llm.util.Util;

/**
 * Base prompt generator for the UI generation pipeline. Loads prompt templates
 * for the generic stages shared by all UI generation models: sanitize input
 * and formalise intent.
 *
 * <p>Subclasses should call {@code super()} and then add their own target-specific
 * prompt templates (e.g. for A2UI conversion).</p>
 */
public class UIGenerationPromptGenerator extends TemplatedPromptGenerator<UIGenerationPromptGenerator> {

    public UIGenerationPromptGenerator() {
        super();
        addPrompt(UIGenerationModelPromptType.FormaliseIntent, Util.loadStringResource("content/prompts/formalise-intent-prompt"));
        addPrompt(UIGenerationModelPromptType.ImpactAnalysis, Util.loadStringResource("content/prompts/ui-impact-analysis-prompt"));
    }
}
