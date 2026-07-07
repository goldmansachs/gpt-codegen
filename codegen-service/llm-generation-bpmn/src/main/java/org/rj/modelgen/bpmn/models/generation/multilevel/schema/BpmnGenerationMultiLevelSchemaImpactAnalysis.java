package org.rj.modelgen.bpmn.models.generation.multilevel.schema;

import org.rj.modelgen.llm.schema.ModelSchema;
import org.rj.modelgen.llm.util.Util;

public class BpmnGenerationMultiLevelSchemaImpactAnalysis extends ModelSchema {

    public BpmnGenerationMultiLevelSchemaImpactAnalysis() {
        super(Util.loadStringResource("content/models/multilevel/bpmn-impact-analysis-schema.json"));
    }
}
