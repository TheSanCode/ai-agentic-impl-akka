package io.agenticawithakka.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.agenticawithakka.connectors.mock.MockDelegatedTokenProvider;
import io.agenticawithakka.connectors.mock.MockSourceConnector;
import io.agenticawithakka.domain.contracts.AgentRole;
import io.agenticawithakka.domain.contracts.Classification;
import io.agenticawithakka.domain.contracts.ContractFixtures;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.EvidenceRef;
import io.agenticawithakka.domain.contracts.IdempotencyKey;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.domain.contracts.ToolArguments;
import io.agenticawithakka.domain.contracts.ToolRef;
import io.agenticawithakka.domain.contracts.ToolRequest;
import io.agenticawithakka.domain.contracts.TaskEnvelope;
import io.agenticawithakka.security.MockIdentityContextStore;
import io.agenticawithakka.security.MockProjectPolicyService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockAdaptersIntegrationTest {
    private static final Instant NOW = ContractFixtures.NOW;
    private static final ProjectId ALPHA = ContractFixtures.ALPHA;
    private static final String SOURCE = "mock-logs";
    private static final byte[] KEY = "synthetic-local-mock-signing-key-32-bytes".getBytes(StandardCharsets.UTF_8);

    @Test
    void policyAndDelegatedSourceAdaptersComposeThroughTheToolGate() {
        var fixture = fixture(Set.of("mock-logs"));
        var outcome = fixture.registry().invoke(
                        fixture.context(),
                        request(fixture.context(), Map.of(
                                "service", "billing",
                                "from", NOW.minusSeconds(60).toString(),
                                "to", NOW.toString())))
                .toCompletableFuture()
                .join();

        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.SUCCEEDED);
        assertThat(outcome.passages()).singleElement().extracting(EvidencePassage::text)
                .isEqualTo("synthetic delegated log evidence");
    }

    @Test
    void projectPolicyAndSourceAclIndependentlyDenyAccess() {
        var resourceDenied = fixture(Set.of(SOURCE));
        var badService = resourceDenied.registry().invoke(
                        resourceDenied.context(),
                        request(resourceDenied.context(), Map.of(
                                "service", "payroll",
                                "from", NOW.minusSeconds(60).toString(),
                                "to", NOW.toString())))
                .toCompletableFuture()
                .join();
        assertThat(badService.status()).isEqualTo(ToolOutcomeStatus.DENIED);

        var sourceDenied = fixture(Set.of());
        var noSourceGrant = sourceDenied.registry().invoke(
                        sourceDenied.context(),
                        request(sourceDenied.context(), Map.of(
                                "service", "billing",
                                "from", NOW.minusSeconds(60).toString(),
                                "to", NOW.toString())))
                .toCompletableFuture()
                .join();
        assertThat(noSourceGrant.status()).isEqualTo(ToolOutcomeStatus.DENIED);
    }

    @Test
    void membershipRevokedAfterTaskCreationDeniesItsNextToolInvocation() {
        var fixture = fixture(Set.of(SOURCE));
        var first = fixture.registry().invoke(
                        fixture.context(),
                        request(fixture.context(), Map.of(
                                "service", "billing",
                                "from", NOW.minusSeconds(60).toString(),
                                "to", NOW.toString())))
                .toCompletableFuture()
                .join();
        assertThat(first.status()).isEqualTo(ToolOutcomeStatus.SUCCEEDED);

        assertThat(fixture.identities().revokeProjectMembership(fixture.identity(), ALPHA)).isTrue();
        var afterRevocation = fixture.registry().invoke(
                        fixture.context(),
                        request(fixture.context(), Map.of(
                                "service", "billing",
                                "from", NOW.minusSeconds(60).toString(),
                                "to", NOW.toString())))
                .toCompletableFuture()
                .join();

        assertThat(afterRevocation.status()).isEqualTo(ToolOutcomeStatus.DENIED);
        assertThat(afterRevocation.passages()).isEmpty();
    }

    private static Fixture fixture(Set<String> sourceGrants) {
        var clock = Clock.fixed(NOW, java.time.ZoneOffset.UTC);
        var identities = new MockIdentityContextStore(clock, Duration.ofMinutes(5));
        var identity = identities.registerAuthenticatedSubject(
                "alpha-member",
                Map.of(ALPHA, new MockIdentityContextStore.ProjectAccess(
                        Set.of("service:billing"), sourceGrants)));
        var policy = new MockProjectPolicyService(identities);
        var tokens = new MockDelegatedTokenProvider(
                identities, clock, Duration.ofMinutes(2), KEY, Map.of(SOURCE, Set.of("logs.read")));
        var evidence = new EvidencePassage(
                new EvidenceRef(SOURCE, "v1", ALPHA, Classification.INTERNAL, NOW, "permission:alpha-logs"),
                "synthetic delegated log evidence");
        var connector = new MockSourceConnector(
                SOURCE,
                clock,
                KEY,
                Map.of("queryLogs", "logs.read"),
                Map.of("queryLogs", List.of(evidence)),
                Map.of(ALPHA, Set.of("alpha-member")),
                identities);
        var base = ContractFixtures.envelope();
        var envelope = new TaskEnvelope(
                base.schemaVersion(),
                base.executionId(),
                base.taskId(),
                base.correlationId(),
                ALPHA,
                identity,
                base.deadline(),
                AgentRole.INVESTIGATION,
                base.input(),
                base.replyRoute(),
                base.budget());
        var context = ToolInvocationContext.from(envelope);
        var registry = new ToolRegistry(
                List.of(new QueryMockLogsTool(
                        connector, tokens, new DelegationTarget(SOURCE, Set.of("logs.read")), clock)),
                Set.of(ToolRisk.READ),
                policy,
                clock);
        return new Fixture(context, registry, identities, identity, envelope);
    }

    private static ToolRequest request(ToolInvocationContext context, Map<String, String> arguments) {
        return new ToolRequest(
                context.executionId(),
                context.taskId(),
                new ToolRef(QueryMockLogsTool.REF.id(), QueryMockLogsTool.REF.version()),
                new ToolArguments(arguments),
                new IdempotencyKey("exec-1:task-1:queryMockLogs:0001"));
    }

    private record Fixture(
            ToolInvocationContext context,
            ToolRegistry registry,
            MockIdentityContextStore identities,
            io.agenticawithakka.domain.contracts.IdentityContextRef identity,
            TaskEnvelope envelope) {}
}
