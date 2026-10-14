package io.agenticawithakka.tools;

/** Structured tool outcome classes (TOL-03); agents must handle each without inventing results. */
public enum ToolOutcomeStatus {
    SUCCEEDED,
    NOT_FOUND,
    UNKNOWN_TOOL,
    INVALID_INPUT,
    DENIED,
    AUTHENTICATION_REQUIRED,
    UNAVAILABLE,
    DEADLINE_EXCEEDED,
    CANCELLED,
    FAILED
}
