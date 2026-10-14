package io.agenticawithakka.tools;

import static io.agenticawithakka.tools.ToolTestSupport.CLOCK;
import static io.agenticawithakka.tools.ToolTestSupport.NOW;
import static io.agenticawithakka.tools.ToolTestSupport.await;
import static io.agenticawithakka.tools.ToolTestSupport.context;
import static io.agenticawithakka.tools.ToolTestSupport.passage;
import static io.agenticawithakka.tools.ToolTestSupport.request;
import static org.assertj.core.api.Assertions.assertThat;

import io.agenticawithakka.application.ports.AdapterCapabilities;
import io.agenticawithakka.application.ports.DelegatedCredential;
import io.agenticawithakka.application.ports.DelegatedTokenProvider;
import io.agenticawithakka.application.ports.DelegationRequest;
import io.agenticawithakka.application.ports.PolicyAction;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.application.ports.SearchGateway;
import io.agenticawithakka.application.ports.SearchQuery;
import io.agenticawithakka.application.ports.SourceConnector;
import io.agenticawithakka.application.ports.SourceReadRequest;
import io.agenticawithakka.domain.contracts.AgentRole;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.ToolRef;
import io.agenticawithakka.tools.ToolTestSupport.FakePolicy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ReadToolsTest {
    private static final DelegationTarget LOGS_TARGET = new DelegationTarget("mock-logs-api", Set.of("logs.read"));
    private static final DelegationTarget HEALTH_TARGET =
            new DelegationTarget("mock-health-api", Set.of("health.read"));

    private final FakePolicy policy = new FakePolicy();
    private final RecordingSearch search = new RecordingSearch();
    private final RecordingConnector logs = new RecordingConnector("mock-logs");
    private final RecordingConnector health = new RecordingConnector("mock-health");
    private final RecordingTokens tokens = new RecordingTokens();
    private final ToolInvocationContext context = context();
    private final ToolRegistry registry =
            PhaseOneReadTools.registry(search, logs, LOGS_TARGET, health, HEALTH_TARGET, tokens, policy, CLOCK);

    private ToolOutcome invoke(ToolRef tool, Map<String, String> arguments) {
        return await(registry.invoke(context, request(context, tool, arguments)));
    }

    private static Map<String, String> logWindow(String service) {
        return Map.of("service", service, "from", NOW.minus(Duration.ofHours(1)).toString(), "to", NOW.toString());
    }

    @Test
    void phaseOneExposesExactlyTheThreeReadToolsToInvestigation() {
        var tools = await(registry.discover(context));

        assertThat(tools).extracting(definition -> definition.ref().id())
                .containsExactly("inspectMockHealth", "queryMockLogs", "searchKnowledge");
        assertThat(tools).allSatisfy(definition -> {
            assertThat(definition.risk()).isEqualTo(ToolRisk.READ);
            assertThat(definition.allowedRoles()).containsExactly(AgentRole.INVESTIGATION);
        });
        assertThat(await(registry.discover(context(AgentRole.COORDINATOR, context.deadline())))).isEmpty();
    }

    @Test
    void writeToolsAreNotRegistered() {
        var outcome = invoke(new ToolRef("restartPod", 1), Map.of("service", "alpha-api"));
        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.UNKNOWN_TOOL);
    }

    @Test
    void searchKnowledgeUsesTrustedContextNotArguments() {
        var outcome = invoke(SearchKnowledgeTool.REF, Map.of("query", "connection timeout runbook"));

        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.SUCCEEDED);
        var query = search.queries.getFirst();
        assertThat(query.projectId()).isEqualTo(context.projectId());
        assertThat(query.identityContextRef()).isEqualTo(context.identityContextRef());
        assertThat(query.deadline()).isEqualTo(context.deadline());
        assertThat(query.maxResults()).isEqualTo(SearchKnowledgeTool.DEFAULT_RESULTS);
    }

    @Test
    void queryMockLogsUsesDelegatedAccessBoundToTrustedContext() {
        var outcome = invoke(QueryMockLogsTool.REF, logWindow("alpha-api"));

        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.SUCCEEDED);
        var delegation = tokens.requests.getFirst();
        assertThat(delegation.identityContextRef()).isEqualTo(context.identityContextRef());
        assertThat(delegation.projectId()).isEqualTo(context.projectId());
        assertThat(delegation.audience()).isEqualTo("mock-logs-api");
        var read = logs.reads.getFirst();
        assertThat(read.operation()).isEqualTo(QueryMockLogsTool.OPERATION);
        assertThat(read.maxRecords()).isEqualTo(QueryMockLogsTool.DEFINITION.maxOutputPassages());
        assertThat(read.projectId()).isEqualTo(context.projectId());
        assertThat(policy.requests).anySatisfy(r -> {
            assertThat(r.action()).isEqualTo(PolicyAction.READ_EVIDENCE);
            assertThat(r.resourceId()).isEqualTo("arg:service:alpha-api");
        });
    }

    @Test
    void invalidWindowNeverReachesDelegationOrSource() {
        var wide = Map.of("service", "alpha-api",
                "from", NOW.minus(Duration.ofHours(12)).toString(), "to", NOW.toString());

        assertThat(invoke(QueryMockLogsTool.REF, wide).status()).isEqualTo(ToolOutcomeStatus.INVALID_INPUT);
        assertThat(tokens.requests).isEmpty();
        assertThat(logs.reads).isEmpty();
    }

    @Test
    void expiredOrMissingDelegationRequiresReauthenticationWithoutFallback() {
        tokens.issue = request -> credential(request.audience(), request.scopes(), NOW);
        assertThat(invoke(QueryMockLogsTool.REF, logWindow("alpha-api")).status())
                .isEqualTo(ToolOutcomeStatus.AUTHENTICATION_REQUIRED);

        tokens.issue = request -> {
            throw new PortException(ErrorCode.AUTHENTICATION_REQUIRED, "consent required");
        };
        assertThat(invoke(InspectMockHealthTool.REF, Map.of("service", "alpha-api")).status())
                .isEqualTo(ToolOutcomeStatus.AUTHENTICATION_REQUIRED);
        assertThat(logs.reads).isEmpty();
        assertThat(health.reads).isEmpty();
    }

    @Test
    void credentialForAnotherAudienceOrNarrowerScopeIsDenied() {
        tokens.issue = request -> credential("mock-health-api", request.scopes(), NOW.plusSeconds(600));
        assertThat(invoke(QueryMockLogsTool.REF, logWindow("alpha-api")).status())
                .isEqualTo(ToolOutcomeStatus.DENIED);

        tokens.issue = request -> credential(request.audience(), Set.of(), NOW.plusSeconds(600));
        assertThat(invoke(QueryMockLogsTool.REF, logWindow("alpha-api")).status())
                .isEqualTo(ToolOutcomeStatus.DENIED);
        assertThat(logs.reads).isEmpty();
    }

    @Test
    void unauthorizedServiceIsDeniedBeforeDelegation() {
        policy.deny(PolicyAction.READ_EVIDENCE, "arg:service:beta-api");

        var outcome = invoke(InspectMockHealthTool.REF, Map.of("service", "beta-api"));

        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.DENIED);
        assertThat(tokens.requests).isEmpty();
        assertThat(health.reads).isEmpty();
    }

    @Test
    void credentialSecretIsNeverExposedInOutcomes() {
        var outcome = invoke(InspectMockHealthTool.REF, Map.of("service", "alpha-api"));
        assertThat(outcome.toString()).doesNotContain(RecordingTokens.SECRET);
        assertThat(health.credentials.getFirst().secretValue()).isEqualTo(RecordingTokens.SECRET);
    }

    private static DelegatedCredential credential(String audience, Set<String> scopes, java.time.Instant expiresAt) {
        return new DelegatedCredential(audience, scopes, expiresAt, RecordingTokens.SECRET);
    }

    private static AdapterCapabilities fakeCapabilities(String name) {
        return new AdapterCapabilities(name, Set.of("read"), List.of("test fake"));
    }

    private static final class RecordingSearch implements SearchGateway {
        final List<SearchQuery> queries = new CopyOnWriteArrayList<>();

        @Override
        public AdapterCapabilities capabilities() {
            return fakeCapabilities("fake-search");
        }

        @Override
        public CompletionStage<List<EvidencePassage>> search(SearchQuery query) {
            queries.add(query);
            return CompletableFuture.completedFuture(List.of(passage("runbook-timeouts", "Check pool size.")));
        }
    }

    private static final class RecordingConnector implements SourceConnector {
        final List<SourceReadRequest> reads = new CopyOnWriteArrayList<>();
        final List<DelegatedCredential> credentials = new CopyOnWriteArrayList<>();
        private final String sourceId;

        RecordingConnector(String sourceId) {
            this.sourceId = sourceId;
        }

        @Override
        public String sourceId() {
            return sourceId;
        }

        @Override
        public AdapterCapabilities capabilities() {
            return fakeCapabilities(sourceId);
        }

        @Override
        public CompletionStage<List<EvidencePassage>> read(SourceReadRequest request, DelegatedCredential credential) {
            reads.add(request);
            credentials.add(credential);
            return CompletableFuture.completedFuture(List.of(passage(sourceId + "-1", "connection timeout")));
        }
    }

    private static final class RecordingTokens implements DelegatedTokenProvider {
        static final String SECRET = "fake-delegated-secret";
        final List<DelegationRequest> requests = new CopyOnWriteArrayList<>();
        volatile Function<DelegationRequest, DelegatedCredential> issue =
                request -> credential(request.audience(), request.scopes(), NOW.plusSeconds(600));

        @Override
        public AdapterCapabilities capabilities() {
            return fakeCapabilities("fake-tokens");
        }

        @Override
        public CompletionStage<DelegatedCredential> acquire(DelegationRequest request) {
            requests.add(request);
            try {
                return CompletableFuture.completedFuture(issue.apply(request));
            } catch (PortException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }
}
