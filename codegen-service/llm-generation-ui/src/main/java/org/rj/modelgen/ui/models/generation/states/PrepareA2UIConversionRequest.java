package org.rj.modelgen.ui.models.generation.states;

import org.rj.modelgen.llm.beans.Prompt;
import org.rj.modelgen.ui.component.A2UIComponentLibrary;
import org.rj.modelgen.ui.component.A2UIComponentLibrarySerializer;
import org.rj.modelgen.llm.component.ComponentLibrarySelector;
import org.rj.modelgen.llm.component.DefaultComponentLibrarySelector;
import org.rj.modelgen.llm.context.Context;
import org.rj.modelgen.llm.context.provider.ContextProvider;
import org.rj.modelgen.llm.prompt.PromptSubstitution;
import org.rj.modelgen.llm.prompt.StandardPromptPlaceholders;
import org.rj.modelgen.llm.prompt.TemplatedPromptGenerator;
import org.rj.modelgen.llm.schema.ModelSchema;
import org.rj.modelgen.llm.statemodel.states.common.PrepareModelGenerationRequest;
import org.rj.modelgen.llm.util.StringSerializable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Specialized preparation state for A2UI conversion requests. Extends
 * {@link PrepareModelGenerationRequest} to inject the A2UI component library catalog
 * into the prompt context via structured serialization.
 *
 * <p>Unlike the generic {@code PrepareGenericModelRequest}, this class is purpose-built
 * for A2UI conversion and populates both the {@code COMPONENT_LIBRARY} and
 * {@code CATALOG_CONTENT} prompt placeholders with the filtered and serialized
 * A2UI component catalog (components + functions).</p>
 *
 * @param <TPromptGenerator>   the prompt generator type used to resolve and render templates
 * @param <TComponentLibrary>  the A2UI component library type (must extend {@link A2UIComponentLibrary})
 */
public class PrepareA2UIConversionRequest<TPromptGenerator extends TemplatedPromptGenerator<?>, TComponentLibrary extends A2UIComponentLibrary>
        extends PrepareModelGenerationRequest {

    private final TPromptGenerator promptGenerator;
    private final StringSerializable promptType;
    private final TComponentLibrary componentLibrary;
    private final ComponentLibrarySelector<TComponentLibrary> componentLibrarySelector;
    private final A2UIComponentLibrarySerializer componentLibrarySerializer;

    /**
     * Constructs a new preparation state with default selector and serializer.
     */
    public PrepareA2UIConversionRequest(ContextProvider contextProvider,
                                        TPromptGenerator promptGenerator,
                                        StringSerializable promptType,
                                        TComponentLibrary componentLibrary) {
        this(contextProvider, promptGenerator, promptType, componentLibrary,
                new DefaultComponentLibrarySelector<>(),
                new A2UIComponentLibrarySerializer());
    }

    /**
     * Constructs a new preparation state with the given selector and serializer, allowing
     * callers to control how the component library is filtered and rendered into the prompt.
     *
     * @param contextProvider            provides and manages conversation context
     * @param promptGenerator            resolves prompt templates for the current a2ui stage
     * @param promptType                 selector key for the prompt template to use
     * @param componentLibrary           the full A2UI component catalog
     * @param componentLibrarySelector   filters the catalog per-request based on payload data
     * @param componentLibrarySerializer serializes the (possibly filtered) catalog into a prompt-ready string
     */
    public PrepareA2UIConversionRequest(ContextProvider contextProvider,
                                        TPromptGenerator promptGenerator,
                                        StringSerializable promptType,
                                        TComponentLibrary componentLibrary,
                                        ComponentLibrarySelector<TComponentLibrary> componentLibrarySelector,
                                        A2UIComponentLibrarySerializer componentLibrarySerializer) {
        super(PrepareA2UIConversionRequest.class, null, contextProvider);
        this.promptGenerator = Objects.requireNonNull(promptGenerator, "promptGenerator must not be null");
        this.promptType = Objects.requireNonNull(promptType, "promptType must not be null");
        this.componentLibrary = Objects.requireNonNull(componentLibrary, "componentLibrary must not be null");
        this.componentLibrarySelector = Optional.ofNullable(componentLibrarySelector)
                .orElseGet(DefaultComponentLibrarySelector::new);
        this.componentLibrarySerializer = Optional.ofNullable(componentLibrarySerializer)
                .orElseGet(A2UIComponentLibrarySerializer::new);
    }

    @Override
    protected Optional<String> buildGenerationPrompt(ModelSchema modelSchema, Context context,
                                                     String request, List<PromptSubstitution> substitutions) {
        return promptGenerator.getPrompt(promptType, substitutions);
    }

    /**
     * Generates additional prompt substitutions specific to A2UI conversion. The component
     * library is filtered via the configured selector, serialized via the A2UI-specific
     * serializer, and injected under both the {@code COMPONENT_LIBRARY} and
     * {@code CATALOG_CONTENT} placeholders so that prompt templates can reference either.
     */
    @Override
    protected List<PromptSubstitution> generateAdditionalPromptSubstitutions(ModelSchema modelSchema,
                                                                             Context context,
                                                                             String request) {
        final var filteredLibrary = componentLibrarySelector.getFilteredLibrary(componentLibrary, getPayload());
        final var serializedCatalog = componentLibrarySerializer.serialize(filteredLibrary);

        return List.of(
                new PromptSubstitution(StandardPromptPlaceholders.COMPONENT_LIBRARY, serializedCatalog),
                new PromptSubstitution(StandardPromptPlaceholders.CATALOG_CONTENT, serializedCatalog)
        );
    }
}
