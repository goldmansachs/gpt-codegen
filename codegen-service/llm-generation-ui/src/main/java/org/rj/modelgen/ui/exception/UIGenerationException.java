package org.rj.modelgen.ui.exception;

/**
 * Exception thrown during UI generation a2ui execution.
 */
public class UIGenerationException extends RuntimeException {

    public UIGenerationException(String message) {
        super(message);
    }

    public UIGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

