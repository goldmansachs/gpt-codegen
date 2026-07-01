package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import org.rj.modelgen.llm.component.ComponentLibrary;
import org.rj.modelgen.llm.context.provider.ContextProvider;
import org.rj.modelgen.llm.prompt.TemplatedPromptGenerator;
import org.rj.modelgen.llm.statemodel.states.common.PrepareAndSubmitLlmGenericRequest;
import org.rj.modelgen.llm.util.StringSerializable;

public class InitializeBpmnPayload<TPromptGenerator extends TemplatedPromptGenerator<TPromptGenerator>, TComponentLibrary extends ComponentLibrary<?>>
        extends PrepareAndSubmitLlmGenericRequest<TPromptGenerator, TComponentLibrary> {

    public InitializeBpmnPayload(ContextProvider contextProvider, TPromptGenerator promptGenerator,
                                 StringSerializable promptType, TComponentLibrary componentLibrary) {
        super(contextProvider, promptGenerator, promptType, componentLibrary);
    }

    @Override
    public String getDescription() {
        return "Generating starting payload variables";
    }
}
