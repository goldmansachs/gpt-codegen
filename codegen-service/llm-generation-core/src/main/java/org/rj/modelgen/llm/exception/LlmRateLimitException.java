package org.rj.modelgen.llm.exception;

public class LlmRateLimitException extends RuntimeException {
    private final int httpStatus;

    public LlmRateLimitException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public LlmRateLimitException(int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
