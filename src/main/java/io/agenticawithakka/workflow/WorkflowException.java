package io.agenticawithakka.workflow;

import io.agenticawithakka.domain.contracts.ErrorCode;

/** Safe API-facing workflow failure without source payloads or implementation details. */
public final class WorkflowException extends RuntimeException {
    private final ErrorCode errorCode;

    public WorkflowException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
