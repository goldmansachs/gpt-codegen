package org.rj.modelgen.ui.models.generation.states;


import org.rj.modelgen.ui.component.A2UIComponentLibrary;
import org.rj.modelgen.ui.component.A2UIComponentLibrarySerializer;
import org.rj.modelgen.ui.models.generation.UIGenerationPromptGenerator;
import org.rj.modelgen.ui.models.generation.a2ui.A2UIPromptGenerator;
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
 * <p>This allows the component library to be filtered per-request (via the selector) and
 * serialized in a format appropriate for the current generation context.</p>
 */
public class PrepareAndSubmitA2UIConversionRequest
        extends PrepareAndSubmitLlmGenerationRequest {

    public PrepareAndSubmitA2UIConversionRequest(
            ContextProvider contextProvider,
            A2UIPromptGenerator promptGenerator,
            StringSerializable promptType,
            A2UIComponentLibrary componentLibrary) {
        this(contextProvider, promptGenerator, promptType, componentLibrary,
                new DefaultComponentLibrarySelector<>(),
                new A2UIComponentLibrarySerializer<>());
    }

    public <TLibrary extends A2UIComponentLibrary, TSerializer extends A2UIComponentLibrarySerializer<TLibrary>>
    PrepareAndSubmitA2UIConversionRequest(
            ContextProvider contextProvider,
            A2UIPromptGenerator promptGenerator,
            StringSerializable promptType,
            TLibrary componentLibrary,
            TSerializer componentLibrarySerializer) {
        this(contextProvider, promptGenerator, promptType, componentLibrary,
                new DefaultComponentLibrarySelector<>(),
                componentLibrarySerializer);
    }

    public <TLibrary extends A2UIComponentLibrary>
    PrepareAndSubmitA2UIConversionRequest(
            ContextProvider contextProvider,
            A2UIPromptGenerator promptGenerator,
            StringSerializable promptType,
            TLibrary componentLibrary,
            ComponentLibrarySelector<TLibrary> componentLibrarySelector,
            A2UIComponentLibrarySerializer<TLibrary> componentLibrarySerializer) {
        super(PrepareAndSubmitA2UIConversionRequest.class,
                buildPreparePhase(contextProvider, promptGenerator, promptType,
                        componentLibrary, componentLibrarySelector, componentLibrarySerializer),
                buildSubmissionPhase());
    }

    private static <TLibrary extends A2UIComponentLibrary>
    PrepareA2UIConversionRequest<UIGenerationPromptGenerator, TLibrary>
    buildPreparePhase(ContextProvider contextProvider,
                      A2UIPromptGenerator promptGenerator,
                      StringSerializable promptType,
                      TLibrary componentLibrary,
                      ComponentLibrarySelector<TLibrary> componentLibrarySelector,
                      A2UIComponentLibrarySerializer<TLibrary> componentLibrarySerializer) {
        return new PrepareA2UIConversionRequest<>(
                contextProvider, promptGenerator, promptType,
                componentLibrary, componentLibrarySelector, componentLibrarySerializer);
    }

    private static SubmitGenericRequestToLlm buildSubmissionPhase() {
        return new SubmitGenericRequestToLlm();
    }
}
