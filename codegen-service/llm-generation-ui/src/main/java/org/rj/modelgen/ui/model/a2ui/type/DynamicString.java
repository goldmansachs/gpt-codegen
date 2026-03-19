package org.rj.modelgen.ui.model.a2ui.type;

// DynamicString can be one of three things:
// 1. A literal String value
// 2. A DataBinding object with a "path" property
// 3. A FunctionCall object with returnType "string"
public sealed interface DynamicString
        permits LiteralString, DataBinding, StringFunctionCall {}

