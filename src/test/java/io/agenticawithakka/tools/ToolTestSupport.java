package io.agenticawithakka.tools;

import io.agenticawithakka.application.ports.AdapterCapabilities;
import io.agenticawithakka.application.ports.PolicyAction;
import io.agenticawithakka.application.ports.PolicyDecision;
import io.agenticawithakka.application.ports.PolicyDecisionService;
import io.agenticawithakka.application.ports.PolicyRequest;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.AgentRole;
import io.agenticawithakka.domain.contracts.ContractFixtures;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.IdempotencyKey;
import io.agenticawithakka.domain.contracts.ToolArguments;
import io.agenticawithakka.domain.contracts.ToolRef;
import io.agenticawithakka.domain.contracts.ToolRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/** Shared fixtures and fakes for tool registry tests. */
final class ToolTestSupport {
    static final Instant NOW = ContractFixtures.NOW;
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private ToolTestSupport() {
    }

    static ToolInvocationContext context() {
        return ToolInvocationContext.from(ContractFixtures.envelope());
    }

    static ToolInvocationContext context(AgentRole role, Instant deadline) {
        var base = context();
        return new ToolInvocationContext(
                base.executionId(), base.taskId(), base.projectId(), base.identityContextRef(), role, deadline);
    }

    static ToolRequest request(ToolInvocationContext context, ToolRef tool, Map<String, String> arguments) {
        return new ToolRequest(context.executionId(), context.taskId(), tool, new ToolArguments(arguments),
                new IdempotencyKey("exec-1:task-1:tool:0001"));
    }

    static EvidencePassage passage(String sourceId, String text) {
        return new EvidencePassage(ContractFixtures.evidence(sourceId), text);
    }

    static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
    }

    static ToolDefinition definition(
            String id, ToolRisk risk, Duration timeout, int retries, int maxPassages, int maxChars) {
        return new ToolDefinition(new ToolRef(id, 1), "Test tool " + id + ".", risk, Set.of(AgentRole.INVESTIGATION),
                List.of(ArgumentSpec.reference("service", true, true), ArgumentSpec.text("query", false, 100)),
                null, timeout, retries, maxPassages, maxChars);
    }

    static CompletionStage<List<EvidencePassage>> passages(EvidencePassage... passages) {
        return CompletableFuture.completedFuture(List.of(passages));
    }

    static CompletionStage<List<EvidencePassage>> failure(ErrorCode code) {
        return CompletableFuture.failedFuture(new PortException(code, "fake failure"));
    }

    /** Permits everything except explicitly denied action/resource pairs. */
    static final class FakePolicy implements PolicyDecisionService {
        final List<PolicyRequest> requests = new CopyOnWriteArrayList<>();
        private final Set<String> denied = ConcurrentHashMap.newKeySet();
        volatile boolean unavailable;

        void deny(PolicyAction action, String resourceId) {
            denied.add(action + "|" + resourceId);
        }

        @Override
        public AdapterCapabilities capabilities() {
            return new AdapterCapabilities("fake-policy", Set.of("decide"), List.of());
        }

        @Override
        public CompletionStage<PolicyDecision> decide(PolicyRequest request) {
            requests.add(request);
            if (unavailable) {
                return CompletableFuture.failedFuture(new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "down"));
            }
            return CompletableFuture.completedFuture(denied.contains(request.action() + "|" + request.resourceId())
                    ? PolicyDecision.deny("policy.denied")
                    : PolicyDecision.permit());
        }
    }

    /** Records calls; behaviour receives the 1-based attempt number. */
    static final class FakeHandler implements ToolHandler {
        final AtomicInteger calls = new AtomicInteger();
        volatile ToolInvocationContext lastContext;
        volatile ToolArguments lastArguments;
        private final ToolDefinition definition;
        private final IntFunction<CompletionStage<List<EvidencePassage>>> behaviour;

        FakeHandler(ToolDefinition definition, IntFunction<CompletionStage<List<EvidencePassage>>> behaviour) {
            this.definition = definition;
            this.behaviour = behaviour;
        }

        @Override
        public ToolDefinition definition() {
            return definition;
        }

        @Override
        public CompletionStage<List<EvidencePassage>> execute(ToolInvocationContext context, ToolArguments arguments) {
            lastContext = context;
            lastArguments = arguments;
            return behaviour.apply(calls.incrementAndGet());
        }
    }
}
