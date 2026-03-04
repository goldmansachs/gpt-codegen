package org.rj.modelgen.ui.models.generation.a2ui;

import org.rj.modelgen.ui.models.generation.UIGenerationPromptGenerator;
import org.rj.modelgen.llm.util.Util;

/**
 * Prompt generator for the A2UI generation pipeline. Extends {@link UIGenerationPromptGenerator}
 * to inherit the base sanitize and formalise intent prompts, and adds the A2UI-specific
 * conversion prompt template.
 */
public class A2UIPromptGenerator extends UIGenerationPromptGenerator {

    public A2UIPromptGenerator() {
        super();
        addPrompt(A2UIPromptType.ConvertToA2UI, Util.loadStringResource("content/prompts/convert-intent-to-a2ui-prompt"));
    }
}
