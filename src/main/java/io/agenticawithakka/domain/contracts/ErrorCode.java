package io.agenticawithakka.domain.contracts;

/** Stable error vocabulary shared by API responses, task results and ports. */
public enum ErrorCode {
    DENIED,
    INVALID_INPUT,
    UNSUPPORTED,
    CONFLICT,
    RATE_LIMITED,
    DEPENDENCY_UNAVAILABLE,
    DEADLINE_EXCEEDED,
    BUDGET_EXHAUSTED,
    AUTHENTICATION_REQUIRED,
    CANCELLED
}
