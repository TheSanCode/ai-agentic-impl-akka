package io.agenticawithakka.connectors.mock;

import io.agenticawithakka.application.ports.AdapterCapabilities;
import io.agenticawithakka.application.ports.DelegatedCredential;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.application.ports.SourceConnector;
import io.agenticawithakka.application.ports.SourceReadRequest;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.security.MockIdentityContextStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Read-only synthetic source that independently verifies mock credential signatures, identity,
 * project, audience, scope, expiry and its own source ACL.
 */
public final class MockSourceConnector implements SourceConnector {
    private final String sourceId;
    private final Clock clock;
    private final MockDelegationTokenCodec codec;
    private final Map<String, String> requiredScopesByOperation;
    private final Map<String, List<EvidencePassage>> passagesByOperation;
    private final Map<ProjectId, Set<String>> allowedSubjects;
    private final MockIdentityContextStore identities;

    public MockSourceConnector(
            String sourceId,
            Clock clock,
            byte[] signingKey,
            Map<String, String> requiredScopesByOperation,
            Map<String, List<EvidencePassage>> passagesByOperation,
            Map<ProjectId, Set<String>> allowedSubjects,
            MockIdentityContextStore identities) {
        this.sourceId = ContractValidation.matches(sourceId, "sourceId", ContractValidation.REFERENCE);
        this.clock = ContractValidation.required(clock, "clock");
        this.identities = ContractValidation.required(identities, "identities");
        this.codec = new MockDelegationTokenCodec(signingKey);
        ContractValidation.required(requiredScopesByOperation, "requiredScopesByOperation");
        if (requiredScopesByOperation.isEmpty() || requiredScopesByOperation.size() > 64) {
            throw new IllegalArgumentException("requiredScopesByOperation must contain between 1 and 64 entries");
        }
        var scopes = new java.util.HashMap<String, String>();
        requiredScopesByOperation.forEach((operation, scope) -> {
            ContractValidation.matches(operation, "operation", ContractValidation.CAMEL_NAME);
            scopes.put(operation, ContractValidation.matches(scope, "scope", ContractValidation.REFERENCE));
        });
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("at least one operation scope is required");
        }
        this.requiredScopesByOperation = Map.copyOf(scopes);

        ContractValidation.required(passagesByOperation, "passagesByOperation");
        if (passagesByOperation.size() > 64) {
            throw new IllegalArgumentException("passagesByOperation exceeds 64 entries");
        }
        var data = new java.util.HashMap<String, List<EvidencePassage>>();
        passagesByOperation.forEach((operation, passages) -> {
            if (!this.requiredScopesByOperation.containsKey(operation)) {
                throw new IllegalArgumentException("passage data has no configured operation");
            }
            var copy = ContractValidation.list(passages, "passages", SourceReadRequest.MAX_RECORDS);
            for (EvidencePassage passage : copy) {
                if (!passage.ref().sourceId().equals(sourceId)) {
                    throw new IllegalArgumentException("passage source does not match connector");
                }
            }
            data.put(operation, copy);
        });
        this.passagesByOperation = Map.copyOf(data);

        ContractValidation.required(allowedSubjects, "allowedSubjects");
        if (allowedSubjects.size() > 32) {
            throw new IllegalArgumentException("allowedSubjects exceeds 32 projects");
        }
        var acl = new java.util.HashMap<ProjectId, Set<String>>();
        allowedSubjects.forEach((project, subjects) -> {
            ContractValidation.required(project, "allowedSubjects.project");
            Set<String> validated = ContractValidation.set(subjects, "allowedSubjects.subjects", 256);
            validated.forEach(subject -> ContractValidation.text(subject, "allowedSubjects.subject", 768));
            acl.put(project, validated);
        });
        this.allowedSubjects = Map.copyOf(acl);
    }

    @Override
    public String sourceId() {
        return sourceId;
    }

    @Override
    public AdapterCapabilities capabilities() {
        return new AdapterCapabilities(
                "mock-source",
                Set.of("read", "delegated-identity", "project-isolation", "source-acl"),
                List.of("Synthetic in-memory data only; does not establish real-source OBO support."));
    }

    @Override
    public CompletionStage<List<EvidencePassage>> read(SourceReadRequest request, DelegatedCredential credential) {
        ContractValidation.required(request, "request");
        try {
            if (credential == null) {
                throw new PortException(ErrorCode.AUTHENTICATION_REQUIRED, "delegated credential is required");
            }
            String requiredScope = requiredScopesByOperation.get(request.operation());
            if (requiredScope == null) {
                throw new PortException(ErrorCode.UNSUPPORTED, "mock source operation is not supported");
            }
            Instant now = clock.instant();
            if (!now.isBefore(request.deadline())) {
                throw new PortException(ErrorCode.DEADLINE_EXCEEDED, "mock source deadline has expired");
            }
            var claims = codec.verify(credential.secretValue());
            Instant tokenExpiry = Instant.ofEpochSecond(claims.expiresAtEpochSecond());
            if (!now.isBefore(tokenExpiry) || credential.expiredAt(now)) {
                throw new PortException(ErrorCode.AUTHENTICATION_REQUIRED, "mock delegated credential has expired");
            }
            if (!claims.audience().equals(sourceId)
                    || !claims.audience().equals(credential.audience())
                    || !claims.scopes().contains(requiredScope)
                    || !claims.scopes().equals(credential.scopes())
                    || !tokenExpiry.equals(credential.expiresAt())
                    || !claims.projectId().equals(request.projectId())
                    || !claims.identityContextRef().equals(request.identityContextRef())) {
                throw new PortException(ErrorCode.DENIED, "mock delegated credential does not match the request");
            }
            var identity = identities.resolve(claims.identityContextRef())
                    .orElseThrow(() ->
                            new PortException(ErrorCode.AUTHENTICATION_REQUIRED, "authenticated identity is unavailable"));
            if (!identity.subject().equals(claims.subject())
                    || !identity.belongsTo(request.projectId())
                    || !identity.mayAccessSource(request.projectId(), sourceId)) {
                throw new PortException(ErrorCode.DENIED, "current identity or source access is not granted");
            }
            if (!allowedSubjects.getOrDefault(request.projectId(), Set.of()).contains(claims.subject())) {
                throw new PortException(ErrorCode.DENIED, "mock source access is not granted");
            }
            List<EvidencePassage> passages = passagesByOperation.getOrDefault(request.operation(), List.of()).stream()
                    .filter(passage -> passage.ref().projectId().equals(request.projectId()))
                    .limit(request.maxRecords())
                    .toList();
            return CompletableFuture.completedFuture(passages);
        } catch (PortException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
