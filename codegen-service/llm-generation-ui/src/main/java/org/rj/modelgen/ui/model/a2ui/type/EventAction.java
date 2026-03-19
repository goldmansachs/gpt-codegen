package org.rj.modelgen.ui.model.a2ui.type;

import java.util.Map;

public record EventAction(
        String name,
        Map<String, Object> context
) implements Action {}
