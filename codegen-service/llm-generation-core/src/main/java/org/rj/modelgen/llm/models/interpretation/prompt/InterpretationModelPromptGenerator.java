package org.rj.modelgen.llm.models.interpretation.prompt;

import org.rj.modelgen.llm.models.generation.multilevel.prompt.MultiLevelModelPromptType;
import org.rj.modelgen.llm.prompt.TemplatedPromptGenerator;

public class InterpretationModelPromptGenerator extends TemplatedPromptGenerator<InterpretationModelPromptGenerator> {
    public static InterpretationModelPromptGenerator create(String sanitizingPrePassPrompt,
                                                                  String generatedModelErrorCorrectionPrompt) {
        return new InterpretationModelPromptGenerator()
                .withAvailablePrompt(MultiLevelModelPromptType.SanitizingPrePass, sanitizingPrePassPrompt)
                .withAvailablePrompt(MultiLevelModelPromptType.CorrectGeneratedModelErrors, generatedModelErrorCorrectionPrompt);
    }

    public InterpretationModelPromptGenerator() {
        super();
    }
}
