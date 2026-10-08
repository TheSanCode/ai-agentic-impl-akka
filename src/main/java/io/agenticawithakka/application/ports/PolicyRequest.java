package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ProjectId;

/** Authorization question built from trusted server state, never from model or client text. */
public record PolicyRequest(
        IdentityContextRef identityContextRef, ProjectId projectId, PolicyAction action, String resourceId) {
    public PolicyRequest {
        ContractValidation.required(identityContextRef, "identityContextRef");
        ContractValidation.required(projectId, "projectId");
        ContractValidation.required(action, "action");
        ContractValidation.matches(resourceId, "resourceId", ContractValidation.REFERENCE);
    }
}
