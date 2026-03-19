package org.rj.modelgen.ui.model.a2ui.type;

public sealed interface DynamicNumber
        permits LiteralNumber, DataBinding, NumberFunctionCall {}