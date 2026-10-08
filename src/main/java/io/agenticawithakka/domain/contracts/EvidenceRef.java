package io.agenticawithakka.domain.contracts;

import java.time.Instant;

/** Traceable pointer to source evidence; access is rechecked before its content is shown. */
public record EvidenceRef(
        String sourceId,
        String sourceVersion,
        ProjectId projectId,
        Classification classification,
        Instant observedAt,
        String permissionRef) {

    public EvidenceRef {
        ContractValidation.matches(sourceId, "evidence.sourceId", ContractValidation.REFERENCE);
        ContractValidation.matches(sourceVersion, "evidence.sourceVersion", ContractValidation.REFERENCE);
        ContractValidation.required(projectId, "evidence.projectId");
        ContractValidation.required(classification, "evidence.classification");
        ContractValidation.required(observedAt, "evidence.observedAt");
        ContractValidation.matches(permissionRef, "evidence.permissionRef", ContractValidation.REFERENCE);
    }
}
