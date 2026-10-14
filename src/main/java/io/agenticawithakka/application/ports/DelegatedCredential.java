package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import java.time.Instant;
import java.util.Set;

/**
 * Short-lived delegated credential. Not a record and exposes no bean getters, so it is not
 * accidentally serialized; {@link #toString()} never reveals the secret. Never place it in
 * prompts, task messages, contracts or logs.
 */
public final class DelegatedCredential {
    private final String audience;
    private final Set<String> scopes;
    private final Instant expiresAt;
    private final String secret;

    public DelegatedCredential(String audience, Set<String> scopes, Instant expiresAt, String secret) {
        this.audience = ContractValidation.matches(audience, "audience", ContractValidation.REFERENCE);
        this.scopes = ContractValidation.set(scopes, "scopes", 32);
        this.expiresAt = ContractValidation.required(expiresAt, "expiresAt");
        this.secret = ContractValidation.required(secret, "secret");
        if (secret.isBlank()) {
            throw new ContractViolationException("secret", "must not be blank");
        }
    }

    public String audience() {
        return audience;
    }

    public Set<String> scopes() {
        return scopes;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean expiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** For connector adapters only. */
    public String secretValue() {
        return secret;
    }

    @Override
    public String toString() {
        return "DelegatedCredential[audience=" + audience + ", scopes=" + scopes
                + ", expiresAt=" + expiresAt + ", secret=[REDACTED]]";
    }
}
