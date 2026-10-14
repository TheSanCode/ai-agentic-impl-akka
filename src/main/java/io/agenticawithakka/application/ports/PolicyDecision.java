package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ContractViolationException;

/** Permit or deny; denials carry a stable reason code safe to log. */
public record PolicyDecision(boolean permitted, String reasonCode) {
    public PolicyDecision {
        if (permitted) {
            if (reasonCode != null) {
                ContractValidation.matches(reasonCode, "reasonCode", ContractValidation.REFERENCE);
            }
        } else {
            if (reasonCode == null) {
                throw new ContractViolationException("reasonCode", "is required for denials");
            }
            ContractValidation.matches(reasonCode, "reasonCode", ContractValidation.REFERENCE);
        }
    }

    public static PolicyDecision permit() {
        return new PolicyDecision(true, null);
    }

    public static PolicyDecision deny(String reasonCode) {
        return new PolicyDecision(false, reasonCode);
    }
}
