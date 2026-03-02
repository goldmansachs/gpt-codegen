package org.rj.modelgen.forms.exception;

/**
 * Exception thrown during form generation pipeline execution.
 */
public class FormGenerationException extends RuntimeException {

    public FormGenerationException(String message) {
        super(message);
    }

    public FormGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

