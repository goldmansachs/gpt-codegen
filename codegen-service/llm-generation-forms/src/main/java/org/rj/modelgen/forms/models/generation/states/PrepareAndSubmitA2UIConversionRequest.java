package org.rj.modelgen.forms.models.generation.states;


import org.rj.modelgen.forms.component.A2UIComponentLibrary;
import org.rj.modelgen.forms.component.A2UIComponentLibrarySerializer;
import org.rj.modelgen.forms.models.generation.pipeline.FormPipelinePromptGenerator;
import org.rj.modelgen.llm.component.ComponentLibrarySelector;
import org.rj.modelgen.llm.component.DefaultComponentLibrarySelector;
import org.rj.modelgen.llm.context.provider.ContextProvider;
import org.rj.modelgen.llm.statemodel.states.common.PrepareAndSubmitLlmGenerationRequest;
import org.rj.modelgen.llm.statemodel.states.common.SubmitGenericRequestToLlm;
import org.rj.modelgen.llm.util.StringSerializable;

/**
 * Specialized prepare-and-submit state for A2UI conversion that injects the A2UI component
 * library into the prompt context via a structured serializer.
 *
 * <p>This allows the component catalog to be filtered per-request (via the selector) and
 * serialized in a format appropriate for the current generation context.</p>
 */
public class PrepareAndSubmitA2UIConversionRequest
        extends PrepareAndSubmitLlmGenerationRequest {

    public PrepareAndSubmitA2UIConversionRequest(
            ContextProvider contextProvider,
            FormPipelinePromptGenerator promptGenerator,
            StringSerializable promptType,
            A2UIComponentLibrary componentLibrary) {
        this(contextProvider, promptGenerator, promptType, componentLibrary,
                new DefaultComponentLibrarySelector<>(),
                new A2UIComponentLibrarySerializer());
    }

    public PrepareAndSubmitA2UIConversionRequest(
            ContextProvider contextProvider,
            FormPipelinePromptGenerator promptGenerator,
            StringSerializable promptType,
            A2UIComponentLibrary componentLibrary,
            ComponentLibrarySelector<A2UIComponentLibrary> componentLibrarySelector,
            A2UIComponentLibrarySerializer componentLibrarySerializer) {
        super(PrepareAndSubmitA2UIConversionRequest.class,
                buildPreparePhase(contextProvider, promptGenerator, promptType,
                        componentLibrary, componentLibrarySelector, componentLibrarySerializer),
                buildSubmissionPhase());
    }

    private static PrepareA2UIConversionRequest<FormPipelinePromptGenerator, A2UIComponentLibrary>
    buildPreparePhase(ContextProvider contextProvider,
                      FormPipelinePromptGenerator promptGenerator,
                      StringSerializable promptType,
                      A2UIComponentLibrary componentLibrary,
                      ComponentLibrarySelector<A2UIComponentLibrary> componentLibrarySelector,
                      A2UIComponentLibrarySerializer componentLibrarySerializer) {
        return new PrepareA2UIConversionRequest<>(
                contextProvider, promptGenerator, promptType,
                componentLibrary, componentLibrarySelector, componentLibrarySerializer);
    }

    private static SubmitGenericRequestToLlm buildSubmissionPhase() {
        return new SubmitGenericRequestToLlm();
    }
}
