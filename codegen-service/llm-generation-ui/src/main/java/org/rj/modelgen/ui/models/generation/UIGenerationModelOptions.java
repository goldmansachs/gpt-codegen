package org.rj.modelgen.ui.models.generation;

import org.rj.modelgen.llm.models.generation.options.GenerationModelOptionsImpl;

/**
 * Base options for UI generation models. Contains options shared across all
 * UI generation model implementations (e.g. A2UI, future formats).
 *
 * <p>Subclasses can extend this to add target-specific options.</p>
 */
public class UIGenerationModelOptions<T extends UIGenerationModelOptions<T>> extends GenerationModelOptionsImpl<T> {

    protected UIGenerationModelOptions() {
        super();
    }

    public static UIGenerationModelOptions<?> defaultOptions() {
        return new UIGenerationModelOptions<>();
    }
}

