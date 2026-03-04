package org.rj.modelgen.ui.models.generation;

import org.rj.modelgen.llm.component.Component;
import org.rj.modelgen.llm.component.ComponentLibrary;

import java.util.ArrayList;
import java.util.List;

/**
 * A no-op component library used for UI generation pipeline stages that perform simple
 * text-to-text transformations and do not require any component library context
 * (e.g. sanitization, intent formalisation).
 */
public class EmptyComponentLibrary extends ComponentLibrary<EmptyComponentLibrary.EmptyComponent> {
    private List<EmptyComponent> components = new ArrayList<>();

    public EmptyComponentLibrary() {
        super();
    }

    @Override
    public ComponentLibrary<EmptyComponent> constructEmpty() {
        return new EmptyComponentLibrary();
    }

    @Override
    public List<EmptyComponent> getComponents() {
        return components;
    }

    @Override
    public void setComponents(List<EmptyComponent> components) {
        this.components = components;
    }

    @Override
    public String defaultSerialize() {
        return "";
    }

    public static EmptyComponentLibrary instance() {
        return new EmptyComponentLibrary();
    }

    /**
     * A no-op component that serializes to an empty string.
     */
    public static class EmptyComponent extends Component {
        @Override
        public String defaultSerialize() {
            return "";
        }
    }
}

