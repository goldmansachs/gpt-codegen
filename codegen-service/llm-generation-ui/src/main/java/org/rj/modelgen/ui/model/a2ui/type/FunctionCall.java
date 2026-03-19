package org.rj.modelgen.ui.model.a2ui.type;

import java.util.Map;

public record FunctionCall(
        String call,                    // required - function name (e.g., "required", "email", "formatDate")
        Map<String, Object> args,       // optional - named arguments
        String returnType               // optional - enum: string, number, boolean, array, object, any, void
) {}