package org.rj.modelgen.ui.model.a2ui.type;

import java.util.Map;

public record BooleanFunctionCall(
        String call,
        Map<String, Object> args,
        String returnType               // must be "boolean"
) implements DynamicBoolean {}
