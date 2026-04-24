package org.rj.modelgen.ui.component;

import org.rj.modelgen.llm.component.Component;

/**
 * Serializes the A2UI component library into a compact summary string containing
 * only component names and descriptions, suitable for high-level prompts where
 * full component detail is not required.
 *
 * Extends the generic {@link ComponentLibrarySummarySerializer} bound to {@link A2UIComponentLibrary}.
 */
public class A2UIComponentLibrarySummarySerializer
        extends ComponentLibrarySummarySerializer<A2UIComponentLibrary> {

    @Override
    protected String serializeOneComponent(Component comp) {
        final var a2uiComp = (A2UIComponent) comp;
        final var sb = new StringBuilder();
        sb.append("**").append(a2uiComp.getComponentType()).append("**");
        if (a2uiComp.getDescription() != null) {
            sb.append(" — ").append(a2uiComp.getDescription());
        }
        return sb.toString();
    }
}
