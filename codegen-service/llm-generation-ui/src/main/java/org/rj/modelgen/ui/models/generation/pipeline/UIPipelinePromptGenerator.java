package org.rj.modelgen.ui.models.generation.pipeline;

import org.rj.modelgen.llm.prompt.TemplatedPromptGenerator;
import org.rj.modelgen.llm.util.Util;

/**
 * Prompt generator for the form generation pipeline. Loads prompt templates
 * for each of the three pipeline stages: sanitize, formalise intent, and convert to A2UI.
 */
public class FormPipelinePromptGenerator extends TemplatedPromptGenerator<FormPipelinePromptGenerator> {

    public FormPipelinePromptGenerator() {
        super();
        addPrompt(FormPipelinePromptType.SanitizeInput, Util.loadStringResource("content/prompts/form-sanitize-input-prompt"));
        addPrompt(FormPipelinePromptType.FormaliseIntent, Util.loadStringResource("content/prompts/form-formalise-intent-prompt"));
        addPrompt(FormPipelinePromptType.ConvertToA2UI, Util.loadStringResource("content/prompts/form-convert-to-a2ui-prompt"));
    }
}

