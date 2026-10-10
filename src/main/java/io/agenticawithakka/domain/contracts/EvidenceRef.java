package io.agenticawithakka.domain.contracts;

import java.time.Instant;

/** Traceable pointer to source evidence; access is rechecked before its content is shown. */
public record EvidenceRef(
        String sourceId,
        String sourceVersion,
        ProjectId projectId,
        Classification classification,
        Instant observedAt,
        String permissionRef,
        String sourceLink) {

    public EvidenceRef {
        ContractValidation.matches(sourceId, "evidence.sourceId", ContractValidation.REFERENCE);
        ContractValidation.matches(sourceVersion, "evidence.sourceVersion", ContractValidation.REFERENCE);
        ContractValidation.required(projectId, "evidence.projectId");
        ContractValidation.required(classification, "evidence.classification");
        ContractValidation.required(observedAt, "evidence.observedAt");
        ContractValidation.matches(permissionRef, "evidence.permissionRef", ContractValidation.REFERENCE);
        if (sourceLink != null) {
            ContractValidation.text(sourceLink, "evidence.sourceLink", 2_048);
            java.net.URI uri;
            try {
                uri = java.net.URI.create(sourceLink);
            } catch (IllegalArgumentException malformed) {
                throw new ContractViolationException("evidence.sourceLink", "must be a valid HTTP(S) URI");
            }
            if (uri.getHost() == null
                    || (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null) {
                throw new ContractViolationException(
                        "evidence.sourceLink", "must be an absolute HTTP(S) URI without user info");
            }
        }
    }

    public EvidenceRef(
            String sourceId,
            String sourceVersion,
            ProjectId projectId,
            Classification classification,
            Instant observedAt,
            String permissionRef) {
        this(sourceId, sourceVersion, projectId, classification, observedAt, permissionRef, null);
    }
}
