package org.rj.modelgen.llm.validation.beans;

import java.util.Objects;

public class IntermediateModelValidationError {
    private String error;
    private String location;

    public IntermediateModelValidationError() {
        this(null);
    }

    public IntermediateModelValidationError(String error) {
        this(error, null);
    }

    public IntermediateModelValidationError(String error, String location) {
        this.error = error;
        this.location = location;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return String.format("%s (at '%s')", error, location);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntermediateModelValidationError)) return false;
        IntermediateModelValidationError that = (IntermediateModelValidationError) o;
        return Objects.equals(error, that.error) && Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(error, location);
    }
}
