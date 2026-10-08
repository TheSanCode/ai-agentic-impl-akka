package io.agenticawithakka.domain.contracts;

/** Persisted execution states; terminal states never transition again. */
public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    AWAITING_APPROVAL,
    AWAITING_AUTHENTICATION,
    AWAITING_BUSINESS_REVIEW,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    public boolean canTransitionTo(ExecutionStatus next) {
        if (isTerminal() || next == null || next == this) {
            return false;
        }
        return switch (this) {
            case QUEUED -> next == RUNNING || next == FAILED || next == CANCELLED;
            case RUNNING -> next != QUEUED;
            case AWAITING_APPROVAL, AWAITING_AUTHENTICATION, AWAITING_BUSINESS_REVIEW ->
                    next == RUNNING || next == FAILED || next == CANCELLED;
            default -> false;
        };
    }
}
