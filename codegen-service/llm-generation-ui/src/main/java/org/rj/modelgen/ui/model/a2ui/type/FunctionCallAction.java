package org.rj.modelgen.ui.model.a2ui.type;

import java.util.Map;

public record FunctionCallAction(
        String call,
        Map<String, Object> args,
        String returnType
) implements Action {}
