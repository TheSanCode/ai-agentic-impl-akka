package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ProjectId;
import java.util.Set;

/** Request for a source-specific delegated credential bound to authenticated identity. */
public record DelegationRequest(
        IdentityContextRef identityContextRef, ProjectId projectId, String audience, Set<String> scopes) {
    public DelegationRequest {
        ContractValidation.required(identityContextRef, "identityContextRef");
        ContractValidation.required(projectId, "projectId");
        ContractValidation.matches(audience, "audience", ContractValidation.REFERENCE);
        scopes = ContractValidation.set(scopes, "scopes", 32);
        for (String scope : scopes) {
            ContractValidation.matches(scope, "scopes", ContractValidation.REFERENCE);
        }
    }
}
