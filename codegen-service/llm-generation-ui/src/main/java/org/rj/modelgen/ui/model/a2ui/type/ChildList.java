package org.rj.modelgen.ui.model.a2ui.type;

// ChildList - either a static array of IDs or a template
public sealed interface ChildList
        permits StaticChildList, TemplateChildList {}

