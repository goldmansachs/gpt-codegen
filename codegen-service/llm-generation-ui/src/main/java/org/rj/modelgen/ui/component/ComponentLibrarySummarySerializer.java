package org.rj.modelgen.ui.component;

import org.rj.modelgen.llm.component.Component;
import org.rj.modelgen.llm.component.ComponentLibrary;
import org.rj.modelgen.llm.component.ComponentLibrarySerializer;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Serializes the component library into a compact summary string containing
 * only component names and descriptions, suitable for high-level prompts where
 * full component detail is not required.
 */
public abstract class ComponentLibrarySummarySerializer<TComponentLibrary extends ComponentLibrary<?>> implements ComponentLibrarySerializer<TComponentLibrary> {

    @Override
    public String serialize(TComponentLibrary library) {
        final var componentSection = serializeComponents(library.getComponents());

        return "--- AVAILABLE COMPONENTS ---\n" +
               "Available components (use the \"component\" field to specify type):\n\n" +
               componentSection;
    }

    private String serializeComponents(List<? extends Component> components) {
        if (components == null || components.isEmpty()) return "(none)";

        return components.stream()
                .map(this::serializeOneComponent)
                .collect(Collectors.joining("\n"));
    }

    protected abstract String serializeOneComponent(Component comp);
}

