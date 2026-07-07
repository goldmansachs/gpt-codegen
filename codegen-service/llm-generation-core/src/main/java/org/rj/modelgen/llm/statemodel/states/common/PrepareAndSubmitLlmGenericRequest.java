package org.rj.modelgen.llm.statemodel.states.common;

import org.rj.modelgen.llm.component.*;
import org.rj.modelgen.llm.context.provider.ContextProvider;
import org.rj.modelgen.llm.prompt.TemplatedPromptGenerator;
import org.rj.modelgen.llm.schema.ModelSchema;
import org.rj.modelgen.llm.util.StringSerializable;

public class PrepareAndSubmitLlmGenericRequest<TPromptGenerator extends TemplatedPromptGenerator<TPromptGenerator>, TComponentLibrary extends ComponentLibrary<?>>
        extends PrepareAndSubmitLlmGenerationRequest {

    public PrepareAndSubmitLlmGenericRequest(ContextProvider contextProvider, TPromptGenerator promptGenerator, StringSerializable promptType,
                                             TComponentLibrary componentLibrary) {
        this(contextProvider, promptGenerator, promptType, componentLibrary,
                new DefaultComponentLibrarySelector<>(),
                new DefaultComponentLibrarySerializer<>());
    }

    public PrepareAndSubmitLlmGenericRequest(ContextProvider contextProvider, TPromptGenerator promptGenerator, StringSerializable promptType,
                                             TComponentLibrary componentLibrary, ModelSchema modelSchema) {
        this(contextProvider, promptGenerator, promptType, componentLibrary,
                new DefaultComponentLibrarySelector<>(),
                new DefaultComponentLibrarySerializer<>(),
                modelSchema);
    }

    public PrepareAndSubmitLlmGenericRequest(ContextProvider contextProvider, TPromptGenerator promptGenerator, StringSerializable promptType,
                                             TComponentLibrary componentLibrary, ComponentLibrarySelector<TComponentLibrary> componentLibrarySelector,
                                             ComponentLibrarySerializer<TComponentLibrary> componentLibrarySerializer) {
        super(PrepareAndSubmitLlmGenericRequest.class,
                buildPreparePhase(contextProvider, promptGenerator, promptType, componentLibrary, componentLibrarySelector, componentLibrarySerializer, null),
                new SubmitGenericRequestToLlm());
    }

    public PrepareAndSubmitLlmGenericRequest(ContextProvider contextProvider, TPromptGenerator promptGenerator, StringSerializable promptType,
                                             TComponentLibrary componentLibrary, ComponentLibrarySelector<TComponentLibrary> componentLibrarySelector,
                                             ComponentLibrarySerializer<TComponentLibrary> componentLibrarySerializer, ModelSchema modelSchema) {
        super(PrepareAndSubmitLlmGenericRequest.class,
                buildPreparePhase(contextProvider, promptGenerator, promptType, componentLibrary, componentLibrarySelector, componentLibrarySerializer, modelSchema),
                new SubmitGenericRequestToLlm());
    }

    private static <TPromptGenerator extends TemplatedPromptGenerator<TPromptGenerator>, TComponentLibrary extends ComponentLibrary<?>>
    PrepareGenericModelRequest<TPromptGenerator, TComponentLibrary> buildPreparePhase(ContextProvider contextProvider, TPromptGenerator promptGenerator,
                                                                                      StringSerializable promptType, TComponentLibrary componentLibrary,
                                                                                      ComponentLibrarySelector<TComponentLibrary> componentLibrarySelector,
                                                                                      ComponentLibrarySerializer<TComponentLibrary> componentLibrarySerializer,
                                                                                      ModelSchema modelSchema) {
        return new PrepareGenericModelRequest<>(PrepareGenericModelRequest.class, contextProvider, promptGenerator, promptType,
                componentLibrary, componentLibrarySelector, componentLibrarySerializer, modelSchema);
    }
}
