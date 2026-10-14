package io.agenticawithakka.connectors.mock;

import io.agenticawithakka.application.ports.AdapterCapabilities;
import io.agenticawithakka.application.ports.DelegatedCredential;
import io.agenticawithakka.application.ports.DelegatedTokenProvider;
import io.agenticawithakka.application.ports.DelegationRequest;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.security.MockIdentityContextStore;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Issues short-lived, source-specific mock credentials bound to a trusted identity and project. */
public final class MockDelegatedTokenProvider implements DelegatedTokenProvider {
    private static final AdapterCapabilities CAPABILITIES = new AdapterCapabilities(
            "mock-delegated-token-provider",
            Set.of("identity-bound", "project-bound", "audience-bound", "scope-bound", "short-lived"),
            List.of("Synthetic HMAC credentials; not OIDC token exchange or a production credential."));
    private final MockIdentityContextStore identities;
    private final Clock clock;
    private final Duration lifetime;
    private final MockDelegationTokenCodec codec;
    private final Map<String, Set<String>> scopesByAudience;

    public MockDelegatedTokenProvider(
            MockIdentityContextStore identities,
            Clock clock,
            Duration lifetime,
            byte[] signingKey,
            Map<String, Set<String>> scopesByAudience) {
        this.identities = ContractValidation.required(identities, "identities");
        this.clock = ContractValidation.required(clock, "clock");
        this.lifetime = ContractValidation.required(lifetime, "lifetime");
        if (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("lifetime must be positive and at most 15 minutes");
        }
        this.codec = new MockDelegationTokenCodec(signingKey);
        ContractValidation.required(scopesByAudience, "scopesByAudience");
        if (scopesByAudience.isEmpty() || scopesByAudience.size() > 32) {
            throw new IllegalArgumentException("scopesByAudience must contain between 1 and 32 entries");
        }
        var copy = new java.util.HashMap<String, Set<String>>();
        scopesByAudience.forEach((audience, scopes) -> {
            ContractValidation.matches(audience, "audience", ContractValidation.REFERENCE);
            Set<String> validated = ContractValidation.set(scopes, "scopes", 32);
            if (validated.isEmpty()) {
                throw new IllegalArgumentException("each mock audience must have at least one scope");
            }
            validated.forEach(scope ->
                    ContractValidation.matches(scope, "scope", ContractValidation.REFERENCE));
            copy.put(audience, validated);
        });
        this.scopesByAudience = Map.copyOf(copy);
    }

    @Override
    public AdapterCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletionStage<DelegatedCredential> acquire(DelegationRequest request) {
        ContractValidation.required(request, "request");
        try {
            var identity = identities.resolve(request.identityContextRef())
                    .orElseThrow(() -> new PortException(
                            ErrorCode.AUTHENTICATION_REQUIRED, "authenticated identity is unavailable"));
            if (!identity.belongsTo(request.projectId())) {
                throw new PortException(ErrorCode.DENIED, "identity is not a member of the requested project");
            }
            if (!identity.mayAccessSource(request.projectId(), request.audience())) {
                throw new PortException(ErrorCode.DENIED, "delegated source access is not granted");
            }
            Set<String> supportedScopes = scopesByAudience.get(request.audience());
            if (supportedScopes == null || request.scopes().isEmpty()
                    || !supportedScopes.containsAll(request.scopes())) {
                throw new PortException(ErrorCode.DENIED, "requested source audience or scopes are not allowed");
            }
            var now = clock.instant();
            var expiresAt = now.plus(lifetime);
            if (identity.expiresAt().isBefore(expiresAt)) {
                expiresAt = identity.expiresAt();
            }
            expiresAt = expiresAt.truncatedTo(ChronoUnit.SECONDS);
            if (!now.isBefore(expiresAt)) {
                throw new PortException(ErrorCode.AUTHENTICATION_REQUIRED, "authenticated identity has expired");
            }
            var claims = new MockDelegationTokenCodec.Claims(
                    identity.subject(),
                    request.projectId(),
                    request.identityContextRef(),
                    request.audience(),
                    expiresAt.getEpochSecond(),
                    request.scopes());
            String token = codec.issue(claims);
            return CompletableFuture.completedFuture(
                    new DelegatedCredential(request.audience(), request.scopes(), expiresAt, token));
        } catch (PortException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
