package io.agenticawithakka.security;

import io.agenticawithakka.domain.contracts.ContractValidation;

/** Stable authorization lookup key formed only from a signature-validated issuer and subject. */
public record OidcPrincipalKey(String issuer, String subject) {
    public OidcPrincipalKey {
        ContractValidation.text(issuer, "issuer", 512);
        ContractValidation.text(subject, "subject", 256);
        if (issuer.indexOf('\n') >= 0 || subject.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("issuer and subject must not contain line breaks");
        }
    }

    public String identityStoreKey() {
        return issuer + "\n" + subject;
    }
}
