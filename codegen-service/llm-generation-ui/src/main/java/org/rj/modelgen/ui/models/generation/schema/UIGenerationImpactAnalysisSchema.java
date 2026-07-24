package org.rj.modelgen.ui.models.generation.schema;

import org.rj.modelgen.llm.schema.ModelSchema;
import org.rj.modelgen.llm.util.Util;

/**
 * JSON schema for the UI impact analysis LLM response.
 */
public class UIGenerationImpactAnalysisSchema extends ModelSchema {

    public UIGenerationImpactAnalysisSchema() {
        super(Util.loadStringResource("content/schemas/ui-impact-analysis-schema.json"));
    }
}

