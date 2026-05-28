package org.rj.modelgen.bpmn.models.generation.validation;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class PayloadVariable {
    String name;
    String type;
    Object example;

    public PayloadVariable() {
    }

    public PayloadVariable(String name, String type) {
        this.name = name;
        this.type = type;
        this.example = getDefaultValue();
    }

    public PayloadVariable(String name, String type, String example) {
        this.name = name;
        this.type = type;
        this.example = example;
    }

    public PayloadVariable(String name, String type, Object example) {
        this.name = name;
        this.type = type;
        this.example = example;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getExample() {
        return example != null ? example : getDefaultValue();
    }

    public void setExample(Object example) {
        this.example = example;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PayloadVariable that = (PayloadVariable) obj;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        return type != null ? type.equals(that.type) : that.type == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result += (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        String exampleStr = getExample() instanceof String ? (String) getExample() : String.valueOf(getExample());
        return String.format("{\"name\":\"%s\",\"type\":\"%s\",\"example\":\"%s\"}", name, type, exampleStr);
    }

    @JsonIgnore
    public String getDefaultValue() {
        if (type == null) {
            return "null";
        }
        return switch (type.toLowerCase()) {
            case "string" -> "\"sample_" + name + "\"";
            case "integer" -> "123";
            case "boolean" -> "true";
            case "float" -> "123.45";
            case "array" -> "[]";
            case "object" -> "{}";
            default -> "null";
        };
    }
}
