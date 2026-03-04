package org.rj.modelgen.ui.models.generation.pipeline;

import org.rj.modelgen.llm.prompt.TemplatedPromptGenerator;
import org.rj.modelgen.llm.util.Util;

/**
 * Prompt generator for the A2UI generation pipeline. Loads prompt templates
 * for each of the three pipeline stages: sanitize, formalise intent, and convert to A2UI.
 */
public class A2UIPipelinePromptGenerator extends TemplatedPromptGenerator<A2UIPipelinePromptGenerator> {

    public A2UIPipelinePromptGenerator() {
        super();
        addPrompt(A2UIPipelinePromptType.SanitizeInput, Util.loadStringResource("content/prompts/sanitize-input-prompt"));
        addPrompt(A2UIPipelinePromptType.FormaliseIntent, Util.loadStringResource("content/prompts/formalise-intent-prompt"));
        addPrompt(A2UIPipelinePromptType.ConvertToA2UI, Util.loadStringResource("content/prompts/convert-intent-to-a2ui-prompt"));
    }
}

