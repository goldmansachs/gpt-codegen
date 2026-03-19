package org.rj.modelgen.ui.model.a2ui.type;

// Shared across all Dynamic types
public record DataBinding(String path)
        implements DynamicString, DynamicBoolean, DynamicNumber, DynamicStringList {}

