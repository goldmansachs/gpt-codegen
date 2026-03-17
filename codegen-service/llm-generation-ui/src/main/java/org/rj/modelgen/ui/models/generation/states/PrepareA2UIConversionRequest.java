package org.rj.modelgen.ui.models.generation.states;

import org.rj.modelgen.llm.component.ComponentLibrarySelector;
import org.rj.modelgen.llm.component.DefaultComponentLibrarySelector;
import org.rj.modelgen.llm.context.provider.ContextProvider;
import org.rj.modelgen.llm.prompt.TemplatedPromptGenerator;
import org.rj.modelgen.llm.statemodel.states.common.PrepareGenericModelRequest;
import org.rj.modelgen.llm.util.StringSerializable;
import org.rj.modelgen.ui.component.A2UIComponentLibrary;
import org.rj.modelgen.ui.component.A2UIComponentLibrarySerializer;

import java.util.Objects;

/**
 * Prepares an LLM generation request for A2UI component conversion.
 *
 * <p>Extends {@link PrepareGenericModelRequest} with A2UI-specific defaults:
 * the component library is serialized using {@link A2UIComponentLibrarySerializer},
 * and the type bound on {@code TComponentLibrary} is restricted to
 * {@link A2UIComponentLibrary} subtypes.</p>
 *
 * <p>The default constructor uses a {@link DefaultComponentLibrarySelector} and
 * {@link A2UIComponentLibrarySerializer} — the full constructor allows these to be
 * overridden for custom filtering or serialization behaviour.</p>
 *
 * @param <TPromptGenerator>   the prompt generator type, must extend {@link TemplatedPromptGenerator}
 * @param <TComponentLibrary>  the A2UI component library type to be serialized into the prompt
 */
public class PrepareA2UIConversionRequest<TPromptGenerator extends TemplatedPromptGenerator<TPromptGenerator>, TComponentLibrary extends A2UIComponentLibrary>
        extends PrepareGenericModelRequest<TPromptGenerator, TComponentLibrary> {

    public PrepareA2UIConversionRequest(ContextProvider contextProvider,
                                        TPromptGenerator promptGenerator,
                                        StringSerializable promptType,
                                        TComponentLibrary componentLibrary) {
        super(contextProvider,
                Objects.requireNonNull(promptGenerator, "promptGenerator must not be null"),
                Objects.requireNonNull(promptType, "promptType must not be null"),
                Objects.requireNonNull(componentLibrary, "componentLibrary must not be null"),
                new DefaultComponentLibrarySelector<>(),
                new A2UIComponentLibrarySerializer<>());
    }

    public PrepareA2UIConversionRequest(ContextProvider contextProvider,
                                        TPromptGenerator promptGenerator,
                                        StringSerializable promptType,
                                        TComponentLibrary componentLibrary,
                                        ComponentLibrarySelector<TComponentLibrary> componentLibrarySelector,
                                        A2UIComponentLibrarySerializer<TComponentLibrary> componentLibrarySerializer) {
        super(contextProvider,
                Objects.requireNonNull(promptGenerator, "promptGenerator must not be null"),
                Objects.requireNonNull(promptType, "promptType must not be null"),
                Objects.requireNonNull(componentLibrary, "componentLibrary must not be null"),
                componentLibrarySelector,
                componentLibrarySerializer);
    }
}
