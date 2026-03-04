package org.rj.modelgen.ui.models.generation.a2ui;

import org.rj.modelgen.ui.models.generation.UIGenerationModelOptions;

/**
 * Options for the A2UI generation model. Extends {@link UIGenerationModelOptions}
 * to inherit shared UI generation options and can add A2UI-specific options.
 */
public class A2UIModelOptions extends UIGenerationModelOptions<A2UIModelOptions> {

    protected A2UIModelOptions() {
        super();
    }

    public static A2UIModelOptions defaultOptions() {
        return new A2UIModelOptions();
    }
}
