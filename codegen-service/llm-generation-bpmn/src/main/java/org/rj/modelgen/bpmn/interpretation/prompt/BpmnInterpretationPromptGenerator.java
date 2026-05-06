package org.rj.modelgen.bpmn.interpretation.prompt;

import org.rj.modelgen.llm.models.interpretation.prompt.InterpretationModelPromptGenerator;
import org.rj.modelgen.llm.models.interpretation.prompt.InterpretationModelPromptType;
import org.rj.modelgen.llm.util.Util;

public class BpmnInterpretationPromptGenerator extends InterpretationModelPromptGenerator {
    public BpmnInterpretationPromptGenerator() {
        addPrompt(InterpretationModelPromptType.InterpretIRModelToRunbookFormat, Util.loadStringResource("content/models/multilevel/bpmn-interpret-intermediate-model-prompt"));
        addPrompt(InterpretationModelPromptType.InterpretRunbookToProse, Util.loadStringResource("content/models/multilevel/bpmn-interpret-to-prose-prompt"));
    }
}
