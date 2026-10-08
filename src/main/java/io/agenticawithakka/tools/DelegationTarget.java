package io.agenticawithakka.tools;

import io.agenticawithakka.domain.contracts.ContractValidation;
import java.util.Set;

/** Configured audience and scopes a source tool requests from the delegated token provider. */
public record DelegationTarget(String audience, Set<String> scopes) {
    public DelegationTarget {
        ContractValidation.matches(audience, "audience", ContractValidation.REFERENCE);
        scopes = ContractValidation.set(scopes, "scopes", 32);
        for (String scope : scopes) {
            ContractValidation.matches(scope, "scopes", ContractValidation.REFERENCE);
        }
    }
}
