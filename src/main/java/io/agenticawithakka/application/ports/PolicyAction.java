package io.agenticawithakka.application.ports;

/** Operations subject to policy decisions in Phase 1. */
public enum PolicyAction {
    START_EXECUTION,
    READ_EXECUTION,
    CANCEL_EXECUTION,
    DISCOVER_TOOL,
    INVOKE_TOOL,
    READ_EVIDENCE
}
