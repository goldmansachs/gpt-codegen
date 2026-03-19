package org.rj.modelgen.ui.model.a2ui.type;

public record AccessibilityAttribute (
        DynamicString label,       // optional - short assistive label (1-3 words)
        DynamicString description  // optional - longer assistive description
) {}