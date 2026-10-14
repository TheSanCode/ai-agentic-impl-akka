package io.agenticawithakka.api;

import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.workflow.WorkflowException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain failures to safe, stable API errors. */
@RestControllerAdvice
public final class ApiExceptionHandler {
    @ExceptionHandler(WorkflowException.class)
    ResponseEntity<ApiError> workflowFailure(WorkflowException failure) {
        return ResponseEntity.status(status(failure.errorCode()))
                .body(new ApiError(failure.errorCode(), failure.getMessage()));
    }

    @ExceptionHandler(PortException.class)
    ResponseEntity<ApiError> portFailure(PortException failure) {
        return ResponseEntity.status(status(failure.errorCode()))
                .body(new ApiError(failure.errorCode(), "operation is not authorized"));
    }

    @ExceptionHandler(ContractViolationException.class)
    ResponseEntity<ApiError> invalidContract(ContractViolationException failure) {
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCode.INVALID_INPUT, "request is invalid"));
    }

    private static HttpStatus status(ErrorCode code) {
        return switch (code) {
            case DENIED -> HttpStatus.FORBIDDEN;
            case AUTHENTICATION_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case CONFLICT, DEADLINE_EXCEEDED, BUDGET_EXHAUSTED -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case CANCELLED, UNSUPPORTED -> HttpStatus.CONFLICT;
        };
    }

    public record ApiError(ErrorCode code, String message) {}
}
