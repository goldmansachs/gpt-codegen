package org.rj.modelgen.ui.model.a2ui.type;

public record CheckRule(
        DynamicBoolean condition,  // required
        String message             // required
) {
}