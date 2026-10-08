package io.agenticawithakka.domain.contracts;

/** Outcome of a single delegated task. */
public enum TaskStatus {
    SUCCEEDED,
    /** Usable but incomplete; requires an error code and stated limitations. */
    PARTIAL,
    FAILED,
    CANCELLED
}
