package io.agenticawithakka.connectors.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.agenticawithakka.application.ports.DelegatedCredential;
import io.agenticawithakka.application.ports.DelegationRequest;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.application.ports.SourceReadRequest;
import io.agenticawithakka.domain.contracts.Classification;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.EvidenceRef;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.domain.contracts.ToolArguments;
import io.agenticawithakka.security.MockIdentityContextStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class MockDelegatedSourceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final ProjectId ALPHA = new ProjectId("alpha");
    private static final ProjectId BETA = new ProjectId("beta");
    private static final byte[] SIGNING_KEY =
            "synthetic-local-mock-signing-key-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final String SOURCE = "mock-logs";
    private static final String SUBJECT = "alice";

    @Test
    void delegatedReadSucceedsOnlyForTheBoundIdentityProjectAudienceAndScope() {
        var fixture = fixture(Set.of(SUBJECT), Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));
        var credential = acquire(fixture.provider(), fixture.identity(), ALPHA, SOURCE, "logs.read");

        var passages = read(fixture.connector(), request(fixture.identity(), ALPHA), credential);
        assertThat(passages).hasSize(1);
        assertThat(passages.getFirst().text()).contains("synthetic timeout");

        assertFailure(
                fixture.connector().read(request(fixture.identity(), BETA), credential),
                ErrorCode.DENIED);
        assertFailure(fixture.connector().read(request(fixture.identity(), ALPHA), null), ErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    void missingIdentityWrongAudienceAndUnsupportedScopeCannotAcquireCredentials() {
        var fixture = fixture(Set.of(SUBJECT), Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));
        var missingIdentity = new IdentityContextRef("ictx_missingidentity");
        assertFailure(
                fixture.provider().acquire(new DelegationRequest(missingIdentity, ALPHA, SOURCE, Set.of("logs.read"))),
                ErrorCode.AUTHENTICATION_REQUIRED);
        assertFailure(
                fixture.provider().acquire(
                        new DelegationRequest(fixture.identity(), BETA, SOURCE, Set.of("logs.read"))),
                ErrorCode.DENIED);
        assertFailure(
                fixture.provider().acquire(
                        new DelegationRequest(fixture.identity(), ALPHA, SOURCE, Set.of("logs.admin"))),
                ErrorCode.DENIED);
        assertFailure(
                fixture.provider().acquire(new DelegationRequest(fixture.identity(), ALPHA, "other-source",
                        Set.of("logs.read"))),
                ErrorCode.DENIED);
    }

    @Test
    void sourceIndependentlyRejectsInvalidSignatureExpiredCredentialAndItsOwnAclDenial() {
        var fixture = fixture(Set.of(), Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));
        var credential = acquire(fixture.provider(), fixture.identity(), ALPHA, SOURCE, "logs.read");
        var modified = new DelegatedCredential(
                credential.audience(),
                credential.scopes(),
                credential.expiresAt(),
                credential.secretValue() + "x");
        assertFailure(fixture.connector().read(request(fixture.identity(), ALPHA), modified), ErrorCode.DENIED);
        assertFailure(fixture.connector().read(request(fixture.identity(), ALPHA), credential), ErrorCode.DENIED);

        var shortLived = fixture(Set.of(SUBJECT), Duration.ofSeconds(1), Clock.fixed(NOW, ZoneOffset.UTC));
        var expiring = acquire(shortLived.provider(), shortLived.identity(), ALPHA, SOURCE, "logs.read");
        var laterConnector = connector(
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC), Set.of(SUBJECT));
        assertFailure(laterConnector.read(request(shortLived.identity(), ALPHA), expiring),
                ErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    void sourceRejectsWrongAudienceAndScopeEvenWhenTheCredentialIsAuthentic() {
        var fixture = fixture(Set.of(SUBJECT), Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));
        var otherAudience = acquire(fixture.provider(), fixture.identity(), ALPHA, "mock-other", "other.read");
        assertFailure(fixture.connector().read(request(fixture.identity(), ALPHA), otherAudience), ErrorCode.DENIED);

        var wrongScopeProvider = new MockDelegatedTokenProvider(
                fixture.store(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                SIGNING_KEY,
                Map.of(SOURCE, Set.of("logs.metadata")));
        var wrongScope = acquire(wrongScopeProvider, fixture.identity(), ALPHA, SOURCE, "logs.metadata");
        assertFailure(fixture.connector().read(request(fixture.identity(), ALPHA), wrongScope), ErrorCode.DENIED);
    }

    private static Fixture fixture(Set<String> sourceAcl, Duration identityLifetime, Clock clock) {
        var store = new MockIdentityContextStore(clock, identityLifetime);
        var identity = store.registerAuthenticatedSubject(
                SUBJECT,
                Map.of(ALPHA, new MockIdentityContextStore.ProjectAccess(
                        Set.of("service:billing"), Set.of(SOURCE, "mock-other"))));
        var provider = new MockDelegatedTokenProvider(
                store,
                clock,
                Duration.ofMinutes(5),
                SIGNING_KEY,
                Map.of(SOURCE, Set.of("logs.read"), "mock-other", Set.of("other.read")));
        return new Fixture(store, identity, provider, connector(clock, sourceAcl));
    }

    private static MockSourceConnector connector(Clock clock, Set<String> sourceAcl) {
        var evidence = new EvidencePassage(
                new EvidenceRef(SOURCE, "v1", ALPHA, Classification.INTERNAL, NOW, "permission:alpha-logs"),
                "synthetic timeout in billing");
        return new MockSourceConnector(
                SOURCE,
                clock,
                SIGNING_KEY,
                Map.of("queryLogs", "logs.read"),
                Map.of("queryLogs", List.of(evidence)),
                Map.of(ALPHA, sourceAcl));
    }

    private static SourceReadRequest request(IdentityContextRef identity, ProjectId project) {
        return new SourceReadRequest(
                project,
                identity,
                "queryLogs",
                new ToolArguments(Map.of("service", "billing")),
                10,
                NOW.plusSeconds(30));
    }

    private static DelegatedCredential acquire(
            MockDelegatedTokenProvider provider,
            IdentityContextRef identity,
            ProjectId project,
            String audience,
            String scope) {
        return provider.acquire(new DelegationRequest(identity, project, audience, Set.of(scope)))
                .toCompletableFuture()
                .join();
    }

    private static List<EvidencePassage> read(
            MockSourceConnector connector, SourceReadRequest request, DelegatedCredential credential) {
        return connector.read(request, credential).toCompletableFuture().join();
    }

    private static void assertFailure(
            java.util.concurrent.CompletionStage<?> stage, ErrorCode expectedCode) {
        CompletionException failure = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
        assertThat(failure.getCause()).isInstanceOf(PortException.class);
        assertThat(((PortException) failure.getCause()).errorCode()).isEqualTo(expectedCode);
    }

    private record Fixture(
            MockIdentityContextStore store,
            IdentityContextRef identity,
            MockDelegatedTokenProvider provider,
            MockSourceConnector connector) {}
}
