package io.agenticawithakka.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agenticawithakka.application.ports.AdapterCapabilities;
import io.agenticawithakka.application.ports.DelegatedCredential;
import io.agenticawithakka.application.ports.DelegatedTokenProvider;
import io.agenticawithakka.application.ports.SearchGateway;
import io.agenticawithakka.application.ports.SearchQuery;
import io.agenticawithakka.application.ports.SourceConnector;
import io.agenticawithakka.application.ports.SourceReadRequest;
import io.agenticawithakka.connectors.mock.MockDelegatedTokenProvider;
import io.agenticawithakka.connectors.mock.MockSourceConnector;
import io.agenticawithakka.domain.contracts.Classification;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.EvidenceRef;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.security.AuthenticatedIdentityContextResolver;
import io.agenticawithakka.security.MockIdentityContextStore;
import io.agenticawithakka.security.MockProjectPolicyService;
import io.agenticawithakka.security.ProjectAccessResolver;
import io.agenticawithakka.security.StoredTaskResultAuthorizer;
import io.agenticawithakka.tools.DelegationTarget;
import io.agenticawithakka.tools.PhaseOneReadTools;
import io.agenticawithakka.tools.ToolRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ProcessLocalInvestigationServiceTest {
    private static final Instant START = Instant.parse("2026-10-09T12:00:00Z");
    private static final ProjectId ALPHA = new ProjectId("alpha");
    private static final ProjectId BETA = new ProjectId("beta");
    private static final String ISSUER = "https://issuer.example";
    private static final byte[] SIGNING_KEY =
            "synthetic-local-workflow-test-signing-key".getBytes(StandardCharsets.UTF_8);

    @Test
    void startsBackgroundExecutionAndRechecksOwnerAndMembershipForResults() {
        var fixture = new Fixture(Duration.ofMinutes(10), query -> {});
        var owner = authentication("alice", fixture.clock.instant().plusSeconds(120));
        var accepted = fixture.service.start(owner, ALPHA, "Investigate billing connection timeouts");
        assertThat(accepted.status()).isEqualTo(io.agenticawithakka.domain.contracts.ExecutionStatus.QUEUED);
        assertThat(accepted.executionId()).isNotBlank();

        fixture.executor.runNext();
        var completed = fixture.service.status(owner, uuid(accepted.executionId()));
        assertThat(completed.status()).isEqualTo(io.agenticawithakka.domain.contracts.ExecutionStatus.SUCCEEDED);
        var result = fixture.service.result(owner, uuid(accepted.executionId()));
        assertThat(result.evidence()).hasSize(2);
        assertThat(result.result().evidenceRefs()).hasSize(2);

        var otherProjectUser = authentication("bob", fixture.clock.instant().plusSeconds(120));
        assertThatThrownBy(() -> fixture.service.status(otherProjectUser, uuid(accepted.executionId())))
                .isInstanceOf(WorkflowException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DENIED);
        assertThatThrownBy(() -> fixture.service.start(owner, BETA, "read beta"))
                .isInstanceOf(WorkflowException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DENIED);

        var identityRef = fixture.identities.resolve(owner);
        assertThat(fixture.store.revokeProjectMembership(identityRef, ALPHA)).isTrue();
        assertThatThrownBy(() -> fixture.service.result(owner, uuid(accepted.executionId())))
                .isInstanceOf(WorkflowException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DENIED);
    }

    @Test
    void cancellationBeforeExecutionPreventsAnyToolInvocation() {
        var searchCalls = new AtomicInteger();
        var fixture = new Fixture(Duration.ofMinutes(10), query -> searchCalls.incrementAndGet());
        var owner = authentication("alice", fixture.clock.instant().plusSeconds(120));
        var queued = fixture.service.start(owner, ALPHA, "Investigate the service");
        var cancelled = fixture.service.cancel(owner, uuid(queued.executionId()));

        assertThat(cancelled.status()).isEqualTo(io.agenticawithakka.domain.contracts.ExecutionStatus.CANCELLED);
        fixture.executor.runNext();
        assertThat(fixture.service.status(owner, uuid(queued.executionId())).status())
                .isEqualTo(io.agenticawithakka.domain.contracts.ExecutionStatus.CANCELLED);
        assertThat(searchCalls).hasValue(0);
    }

    @Test
    void cancellationDuringReadPreventsNextToolAndIgnoresLateEvidence() {
        var fixture = new Fixture(Duration.ofMinutes(10), query -> {});
        fixture.pendingSearch = new CompletableFuture<>();
        var owner = authentication("alice", fixture.clock.instant().plusSeconds(120));
        var queued = fixture.service.start(owner, ALPHA, "Investigate the service");

        fixture.executor.runNext();
        assertThat(fixture.service.status(owner, uuid(queued.executionId())).status())
                .isEqualTo(io.agenticawithakka.domain.contracts.ExecutionStatus.RUNNING);
        fixture.service.cancel(owner, uuid(queued.executionId()));
        fixture.pendingSearch.complete(List.of(
                Fixture.passage("mock-knowledge", ALPHA, fixture.clock.instant(), "Late synthetic evidence.")));

        var result = fixture.service.result(owner, uuid(queued.executionId()));
        assertThat(result.result().status()).isEqualTo(io.agenticawithakka.domain.contracts.TaskStatus.CANCELLED);
        assertThat(result.evidence()).isEmpty();
        assertThat(fixture.sourceReads).hasValue(0);
    }

    @Test
    void membershipRevokedDuringExecutionPreventsEvidenceRelease() {
        var identityRef = new AtomicReference<IdentityContextRef>();
        var fixture = new Fixture(Duration.ofMinutes(10), query -> {}, identityRef);
        var owner = authentication("alice", fixture.clock.instant().plusSeconds(120));
        identityRef.set(fixture.identities.resolve(owner));
        var queued = fixture.service.start(owner, ALPHA, "Investigate the service");
        fixture.onSearch = query -> fixture.store.revokeProjectMembership(identityRef.get(), ALPHA);
        fixture.executor.runNext();

        assertThatThrownBy(() -> fixture.service.status(owner, uuid(queued.executionId())))
                .isInstanceOf(WorkflowException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DENIED);
        assertThatThrownBy(() -> fixture.service.result(owner, uuid(queued.executionId())))
                .isInstanceOf(WorkflowException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DENIED);
    }

    @Test
    void expiredIdentityPausesAndSameSubjectCanResumeWithFreshAuthentication() {
        var fixture = new Fixture(Duration.ofSeconds(1), query -> {});
        var owner = authentication("alice", fixture.clock.instant().plusSeconds(120));
        var queued = fixture.service.start(owner, ALPHA, "Investigate the service");
        fixture.onSearch = query -> fixture.clock.advance(Duration.ofSeconds(2));
        fixture.executor.runNext();

        var waiting = fixture.service.status(
                authentication("alice", fixture.clock.instant().plusSeconds(120)), uuid(queued.executionId()));
        assertThat(waiting.status())
                .isEqualTo(io.agenticawithakka.domain.contracts.ExecutionStatus.AWAITING_AUTHENTICATION);

        fixture.service.resume(
                authentication("alice", fixture.clock.instant().plusSeconds(120)), uuid(queued.executionId()));
        fixture.executor.runNext();
        var refreshed = authentication("alice", fixture.clock.instant().plusSeconds(120));
        var completed = fixture.service.status(refreshed, uuid(queued.executionId()));
        assertThat(completed.status()).isEqualTo(io.agenticawithakka.domain.contracts.ExecutionStatus.PARTIAL);
        var result = fixture.service.result(refreshed, uuid(queued.executionId()));
        assertThat(result.result().status()).isEqualTo(io.agenticawithakka.domain.contracts.TaskStatus.PARTIAL);
        assertThat(result.evidence()).singleElement().extracting(evidence -> evidence.ref().sourceId())
                .isEqualTo("mock-logs");
    }

    @Test
    void repeatedAuthenticationExpiryCannotExceedCumulativeStepBudget() {
        var fixture = new Fixture(Duration.ofNanos(1), query -> {});
        var owner = authentication("alice", fixture.clock.instant().plusSeconds(120));
        var queued = fixture.service.start(owner, ALPHA, "Investigate the service");
        fixture.clock.advance(Duration.ofNanos(2));
        fixture.executor.runNext();

        for (int attempt = 1; attempt < ProcessLocalInvestigationService.MAX_CUMULATIVE_STEPS; attempt++) {
            var renewed = authentication("alice", fixture.clock.instant().plusSeconds(120));
            fixture.service.resume(renewed, uuid(queued.executionId()));
            fixture.clock.advance(Duration.ofNanos(2));
            fixture.executor.runNext();
        }
        assertThat(fixture.service.status(
                        authentication("alice", fixture.clock.instant().plusSeconds(120)),
                        uuid(queued.executionId()))
                .steps())
                .isEqualTo(ProcessLocalInvestigationService.MAX_CUMULATIVE_STEPS);
        assertThatThrownBy(() -> fixture.service.resume(
                        authentication("alice", fixture.clock.instant().plusSeconds(120)),
                        uuid(queued.executionId())))
                .isInstanceOf(WorkflowException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUDGET_EXHAUSTED);
    }

    private static JwtAuthenticationToken authentication(String subject, Instant expiresAt) {
        var issuedAt = expiresAt.minusSeconds(120);
        var jwt = new Jwt(
                "test-token-" + subject,
                issuedAt,
                expiresAt,
                Map.of("alg", "none"),
                Map.of("iss", ISSUER, "sub", subject, "aud", List.of("agenticawithakka-api")));
        var authentication = new JwtAuthenticationToken(jwt);
        authentication.setAuthenticated(true);
        return authentication;
    }

    private static java.util.UUID uuid(String value) {
        return java.util.UUID.fromString(value);
    }

    private static final class Fixture {
        private final MutableClock clock = new MutableClock(START);
        private final MockIdentityContextStore store;
        private final AuthenticatedIdentityContextResolver identities;
        private final QueuedExecutor executor = new QueuedExecutor();
        private final ProcessLocalInvestigationService service;
        private final AtomicInteger sourceReads = new AtomicInteger();
        private CompletableFuture<List<EvidencePassage>> pendingSearch;
        private SearchAction onSearch;

        private Fixture(Duration identityLifetime, SearchAction initialSearchAction) {
            this(identityLifetime, initialSearchAction, new AtomicReference<>());
        }

        private Fixture(
                Duration identityLifetime,
                SearchAction initialSearchAction,
                AtomicReference<IdentityContextRef> ref) {
            store = new MockIdentityContextStore(clock, identityLifetime);
            ProjectAccessResolver grants = principal -> {
                if (principal.subject().equals("alice")) {
                    return java.util.Optional.of(Map.of(ALPHA, access()));
                }
                if (principal.subject().equals("bob")) {
                    return java.util.Optional.of(Map.of(BETA, access()));
                }
                return java.util.Optional.empty();
            };
            identities = new AuthenticatedIdentityContextResolver(store, grants, clock);
            var policy = new MockProjectPolicyService(store);
            var search = new SearchGateway() {
                @Override
                public AdapterCapabilities capabilities() {
                    return new AdapterCapabilities("test-search", Set.of("synthetic"), List.of());
                }

                @Override
                public java.util.concurrent.CompletionStage<List<EvidencePassage>> search(SearchQuery query) {
                    onSearch.onSearch(query);
                    if (Fixture.this.pendingSearch != null) {
                        return Fixture.this.pendingSearch;
                    }
                    var current = store.resolve(query.identityContextRef()).orElse(null);
                    if (current == null || !current.belongsTo(query.projectId())
                            || !current.mayAccessSource(query.projectId(), "mock-knowledge")) {
                        return java.util.concurrent.CompletableFuture.completedFuture(List.of());
                    }
                    return java.util.concurrent.CompletableFuture.completedFuture(List.of(passage(
                            "mock-knowledge", query.projectId(), clock.instant(), "Synthetic runbook evidence.")));
                }
            };
            SourceConnector logAdapter = connector("mock-logs", "queryLogs", "logs.read", "Synthetic log evidence.");
            SourceConnector logs = new SourceConnector() {
                @Override
                public String sourceId() {
                    return logAdapter.sourceId();
                }

                @Override
                public AdapterCapabilities capabilities() {
                    return logAdapter.capabilities();
                }

                @Override
                public CompletionStage<List<EvidencePassage>> read(
                        SourceReadRequest request, DelegatedCredential credential) {
                    sourceReads.incrementAndGet();
                    return logAdapter.read(request, credential);
                }
            };
            var health = connector("mock-health", "inspectHealth", "health.read", "Synthetic health evidence.");
            DelegatedTokenProvider tokens = new MockDelegatedTokenProvider(
                    store,
                    clock,
                    Duration.ofSeconds(30),
                    SIGNING_KEY,
                    Map.of("mock-logs", Set.of("logs.read"), "mock-health", Set.of("health.read")));
            ToolRegistry registry = PhaseOneReadTools.registry(
                    search,
                    logs,
                    new DelegationTarget("mock-logs", Set.of("logs.read")),
                    health,
                    new DelegationTarget("mock-health", Set.of("health.read")),
                    tokens,
                    policy,
                    clock);
            var coordinator = new io.agenticawithakka.agents.Coordinator(
                    new io.agenticawithakka.agents.InvestigationAgent(registry, clock), clock);
            service = new ProcessLocalInvestigationService(
                    identities,
                    store,
                    policy,
                    new StoredTaskResultAuthorizer(store, policy),
                    coordinator,
                    executor,
                    clock);
            onSearch = initialSearchAction;
        }

        private MockSourceConnector connector(
                String source, String operation, String scope, String content) {
            return new MockSourceConnector(
                    source,
                    clock,
                    SIGNING_KEY,
                    Map.of(operation, scope),
                    Map.of(operation, List.of(passage(source, ALPHA, clock.instant(), content))),
                    Map.of(ALPHA, Set.of(ISSUER + "\nalice")),
                    store);
        }

        private static MockIdentityContextStore.ProjectAccess access() {
            return new MockIdentityContextStore.ProjectAccess(
                    Set.of("service:billing"), Set.of("mock-knowledge", "mock-logs", "mock-health"));
        }

        private static EvidencePassage passage(String source, ProjectId project, Instant observedAt, String text) {
            return new EvidencePassage(
                    new EvidenceRef(source, "test-v1", project, Classification.INTERNAL, observedAt,
                            "permission:" + project.value()),
                    text);
        }
    }

    @FunctionalInterface
    private interface SearchAction {
        void onSearch(SearchQuery query);
    }

    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        void runNext() {
            Runnable task = tasks.pollFirst();
            if (task == null) {
                throw new AssertionError("no queued investigation");
            }
            task.run();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        synchronized void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public synchronized Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
