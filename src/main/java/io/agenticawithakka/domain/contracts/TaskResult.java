package io.agenticawithakka.domain.contracts;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/** Typed result of a delegated task; failures are explicit rather than fabricated success. */
public record TaskResult(
        TaskId taskId,
        TaskStatus status,
        List<EvidenceRef> evidenceRefs,
        List<Finding> findings,
        List<String> limitations,
        ErrorCode errorCode,
        Instant completedAt) {

    public static final int MAX_EVIDENCE = 200;
    public static final int MAX_FINDINGS = 50;
    public static final int MAX_LIMITATIONS = 20;
    public static final int MAX_LIMITATION_LENGTH = 1_000;

    public TaskResult {
        ContractValidation.required(taskId, "taskId");
        ContractValidation.required(status, "status");
        evidenceRefs = ContractValidation.list(evidenceRefs, "evidenceRefs", MAX_EVIDENCE);
        findings = ContractValidation.list(findings, "findings", MAX_FINDINGS);
        limitations = ContractValidation.list(limitations, "limitations", MAX_LIMITATIONS);
        for (String limitation : limitations) {
            ContractValidation.text(limitation, "limitations", MAX_LIMITATION_LENGTH);
        }
        ContractValidation.required(completedAt, "completedAt");

        switch (status) {
            case SUCCEEDED -> {
                if (errorCode != null) {
                    throw new ContractViolationException("errorCode", "must be absent on success");
                }
            }
            case PARTIAL -> {
                ContractValidation.required(errorCode, "errorCode");
                if (limitations.isEmpty()) {
                    throw new ContractViolationException("limitations", "partial results must state limitations");
                }
            }
            case FAILED -> {
                ContractValidation.required(errorCode, "errorCode");
                if (errorCode == ErrorCode.CANCELLED) {
                    throw new ContractViolationException("errorCode", "use CANCELLED status for cancellation");
                }
            }
            case CANCELLED -> {
                if (errorCode != ErrorCode.CANCELLED) {
                    throw new ContractViolationException("errorCode", "must be CANCELLED for cancelled tasks");
                }
            }
        }

        var cited = new HashSet<>(evidenceRefs);
        for (Finding finding : findings) {
            if (!cited.containsAll(finding.evidenceRefs())) {
                throw new ContractViolationException("findings", "cite evidence absent from evidenceRefs");
            }
        }
    }
}
