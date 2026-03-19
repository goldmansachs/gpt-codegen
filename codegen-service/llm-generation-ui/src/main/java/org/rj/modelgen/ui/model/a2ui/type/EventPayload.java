package org.rj.modelgen.ui.model.a2ui.type;

import java.util.Map;

public record EventPayload(
        String name,                       // required
        Map<String, Object> context        // optional - values can be DynamicValue
) {
}
