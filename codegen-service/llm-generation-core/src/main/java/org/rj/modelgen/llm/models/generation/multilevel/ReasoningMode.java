package org.rj.modelgen.llm.models.generation.multilevel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReasoningMode {
    FLASH,
    MEDIUM,
    REASONING;

    ReasoningMode() {
    }

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static ReasoningMode fromJson(String value) {
        if (value == null) return null;
        return valueOf(value.toUpperCase());
    }
}
