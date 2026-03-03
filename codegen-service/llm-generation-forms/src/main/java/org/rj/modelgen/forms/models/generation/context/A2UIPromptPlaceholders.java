package org.rj.modelgen.forms.models.generation.context;

import org.rj.modelgen.llm.prompt.PromptPlaceholder;
import org.rj.modelgen.llm.prompt.StandardPromptPlaceholders;

public interface A2UIPromptPlaceholders extends StandardPromptPlaceholders {
    PromptPlaceholder CATALOG_CONTENT = new PromptPlaceholder("CATALOG_CONTENT");
}

