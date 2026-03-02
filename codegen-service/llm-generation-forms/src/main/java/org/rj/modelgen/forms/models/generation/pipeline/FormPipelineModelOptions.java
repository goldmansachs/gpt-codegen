package org.rj.modelgen.forms.models.generation.pipeline;

import org.rj.modelgen.llm.models.generation.options.GenerationModelOptionsImpl;

/**
 * Options for the form generation pipeline model.
 */
public class FormPipelineModelOptions extends GenerationModelOptionsImpl<FormPipelineModelOptions> {

    protected FormPipelineModelOptions() {
        super();
    }

    public static FormPipelineModelOptions defaultOptions() {
        return new FormPipelineModelOptions();
    }
}

