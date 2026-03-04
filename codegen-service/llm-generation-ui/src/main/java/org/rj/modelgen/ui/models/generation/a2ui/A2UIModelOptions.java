package org.rj.modelgen.ui.models.generation.pipeline;

import org.rj.modelgen.llm.models.generation.options.GenerationModelOptionsImpl;

/**
 * Options for the A2UI generation pipeline model.
 */
public class A2UIPipelineModelOptions extends GenerationModelOptionsImpl<A2UIPipelineModelOptions> {

    protected A2UIPipelineModelOptions() {
        super();
    }

    public static A2UIPipelineModelOptions defaultOptions() {
        return new A2UIPipelineModelOptions();
    }
}

