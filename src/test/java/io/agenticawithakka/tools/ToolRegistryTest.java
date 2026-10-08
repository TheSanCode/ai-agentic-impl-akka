package io.agenticawithakka.tools;

import static io.agenticawithakka.tools.ToolTestSupport.CLOCK;
import static io.agenticawithakka.tools.ToolTestSupport.NOW;
import static io.agenticawithakka.tools.ToolTestSupport.await;
import static io.agenticawithakka.tools.ToolTestSupport.context;
import static io.agenticawithakka.tools.ToolTestSupport.definition;
import static io.agenticawithakka.tools.ToolTestSupport.failure;
import static io.agenticawithakka.tools.ToolTestSupport.passage;
import static io.agenticawithakka.tools.ToolTestSupport.passages;
import static io.agenticawithakka.tools.ToolTestSupport.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agenticawithakka.application.ports.PolicyAction;
import io.agenticawithakka.domain.contracts.AgentRole;
import io.agenticawithakka.domain.contracts.Classification;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.EvidenceRef;
import io.agenticawithakka.domain.contracts.ExecutionId;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.domain.contracts.ToolRef;
import io.agenticawithakka.domain.contracts.ToolRequest;
import io.agenticawithakka.tools.ToolTestSupport.FakeHandler;
import io.agenticawithakka.tools.ToolTestSupport.FakePolicy;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {
    private static final ToolDefinition SERVICE_TOOL =
            definition("fakeLogs", ToolRisk.READ, Duration.ofSeconds(2), 1, 2, 1_000);
    private static final Map<String, String> ALPHA_API = Map.of("service", "alpha-api");

    private final FakePolicy policy = new FakePolicy();
    private final ToolInvocationContext context = context();

    private ToolRegistry registry(ToolHandler... handlers) {
        return new ToolRegistry(List.of(handlers), PhaseOneReadTools.ENABLED_RISKS, policy, CLOCK);
    }

    private ToolOutcome invoke(ToolRegistry registry, ToolDefinition definition, Map<String, String> arguments) {
        return await(registry.invoke(context, request(context, definition.ref(), arguments)));
    }

    private FakeHandler handler(EvidencePassage... results) {
        return new FakeHandler(SERVICE_TOOL, attempt -> passages(results));
    }

    @Test
    void readOnlyInstallationRejectsDraftAndWriteTools() {
        for (ToolRisk risk : List.of(ToolRisk.DRAFT, ToolRisk.WRITE)) {
            var write = new FakeHandler(definition("restartPod", risk, Duration.ofSeconds(1), 0, 1, 10),
                    attempt -> passages());
            assertThatThrownBy(() -> registry(write))
                    .isInstanceOfSatisfying(ContractViolationException.class,
                            e -> assertThat(e.field()).isEqualTo("tool.risk"));
        }
    }

    @Test
    void rejectsDuplicateToolIds() {
        assertThatThrownBy(() -> registry(handler(), handler())).isInstanceOf(ContractViolationException.class);
    }

    @Test
    void discoveryIsScopedToRoleAndPolicy() {
        var hidden = new FakeHandler(definition("hiddenTool", ToolRisk.READ, Duration.ofSeconds(1), 0, 1, 10),
                attempt -> passages());
        policy.deny(PolicyAction.DISCOVER_TOOL, "tool:hiddenTool:1");
        var registry = registry(handler(), hidden);

        assertThat(await(registry.discover(context))).extracting(ToolDefinition::ref)
                .containsExactly(SERVICE_TOOL.ref());
        assertThat(await(registry.discover(context(AgentRole.COORDINATOR, context.deadline())))).isEmpty();
    }

    @Test
    void discoveryFailsClosedWhenPolicyIsUnavailable() {
        policy.unavailable = true;
        assertThat(await(registry(handler()).discover(context))).isEmpty();
    }

    @Test
    void unknownToolOrVersionIsRejectedBeforeExecution() {
        var tool = handler(passage("kb-1", "text"));
        var registry = registry(tool);

        for (ToolRef ref : List.of(new ToolRef("restartPod", 1), new ToolRef("fakeLogs", 2))) {
            var outcome = await(registry.invoke(context, request(context, ref, ALPHA_API)));
            assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.UNKNOWN_TOOL);
        }
        assertThat(tool.calls).hasValue(0);
        assertThat(policy.requests).isEmpty();
    }

    @Test
    void forbiddenRoleIsDeniedBeforeExecution() {
        var tool = handler(passage("kb-1", "text"));
        var coordinator = context(AgentRole.COORDINATOR, context.deadline());
        var outcome = await(registry(tool).invoke(coordinator, request(coordinator, SERVICE_TOOL.ref(), ALPHA_API)));

        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.DENIED);
        assertThat(outcome.reasonCode()).isEqualTo("tool.role");
        assertThat(tool.calls).hasValue(0);
    }

    @Test
    void invalidOrForgedArgumentsAreRejectedBeforeExecution() {
        var tool = handler(passage("kb-1", "text"));
        var registry = registry(tool);

        var forged = invoke(registry, SERVICE_TOOL, Map.of("service", "alpha-api", "userId", "admin"));
        var malformed = invoke(registry, SERVICE_TOOL, Map.of("service", "alpha api"));

        assertThat(forged.status()).isEqualTo(ToolOutcomeStatus.INVALID_INPUT);
        assertThat(forged.reasonCode()).isEqualTo("invalid:arguments").doesNotContain("admin");
        assertThat(malformed.reasonCode()).isEqualTo("invalid:arguments.service");
        assertThat(tool.calls).hasValue(0);
        assertThat(policy.requests).isEmpty();
    }

    @Test
    void mismatchedTrustedContextIsDenied() {
        var tool = handler(passage("kb-1", "text"));
        var foreign = new ToolRequest(ExecutionId.random(), context.taskId(), SERVICE_TOOL.ref(),
                request(context, SERVICE_TOOL.ref(), ALPHA_API).validatedArguments(),
                request(context, SERVICE_TOOL.ref(), ALPHA_API).idempotencyKey());

        var outcome = await(registry(tool).invoke(context, foreign));

        assertThat(outcome.reasonCode()).isEqualTo("context.mismatch");
        assertThat(tool.calls).hasValue(0);
    }

    @Test
    void expiredDeadlineStopsBeforePolicyOrExecution() {
        var tool = handler(passage("kb-1", "text"));
        var expired = context(AgentRole.INVESTIGATION, NOW);
        var outcome = await(registry(tool).invoke(expired, request(expired, SERVICE_TOOL.ref(), ALPHA_API)));

        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.DEADLINE_EXCEEDED);
        assertThat(tool.calls).hasValue(0);
        assertThat(policy.requests).isEmpty();
    }

    @Test
    void policyDenialOfToolOrResourcePreventsExecution() {
        var tool = handler(passage("kb-1", "text"));
        var registry = registry(tool);
        policy.deny(PolicyAction.READ_EVIDENCE, "arg:service:beta-api");

        var resourceDenied = invoke(registry, SERVICE_TOOL, Map.of("service", "beta-api"));
        policy.deny(PolicyAction.INVOKE_TOOL, "tool:fakeLogs:1");
        var toolDenied = invoke(registry, SERVICE_TOOL, ALPHA_API);

        assertThat(resourceDenied.status()).isEqualTo(ToolOutcomeStatus.DENIED);
        assertThat(toolDenied.status()).isEqualTo(ToolOutcomeStatus.DENIED);
        assertThat(tool.calls).hasValue(0);
        assertThat(policy.requests).allSatisfy(r -> {
            assertThat(r.identityContextRef()).isEqualTo(context.identityContextRef());
            assertThat(r.projectId()).isEqualTo(context.projectId());
        });
    }

    @Test
    void unavailablePolicyFailsClosed() {
        var tool = handler(passage("kb-1", "text"));
        policy.unavailable = true;

        var outcome = invoke(registry(tool), SERVICE_TOOL, ALPHA_API);

        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.UNAVAILABLE);
        assertThat(tool.calls).hasValue(0);
    }

    @Test
    void successPassesTrustedContextAndBoundsPassageCount() {
        var tool = handler(passage("kb-1", "one"), passage("kb-2", "two"), passage("kb-3", "three"));

        var outcome = invoke(registry(tool), SERVICE_TOOL, ALPHA_API);

        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.SUCCEEDED);
        assertThat(outcome.passages()).extracting(EvidencePassage::text).containsExactly("one", "two");
        assertThat(outcome.truncated()).isTrue();
        assertThat(tool.lastContext).isEqualTo(context);
        assertThat(tool.lastArguments.values()).isEqualTo(ALPHA_API);
    }

    @Test
    void boundsOutputCharacters() {
        var small = definition("smallTool", ToolRisk.READ, Duration.ofSeconds(1), 0, 5, 10);
        var tool = new FakeHandler(small, attempt -> passages(passage("kb-1", "12345678"), passage("kb-2", "123")));
        var tooLarge = new FakeHandler(definition("tinyTool", ToolRisk.READ, Duration.ofSeconds(1), 0, 5, 2),
                attempt -> passages(passage("kb-1", "123")));
        var registry = registry(tool, tooLarge);

        var outcome = invoke(registry, small, ALPHA_API);
        assertThat(outcome.passages()).hasSize(1);
        assertThat(outcome.truncated()).isTrue();
        assertThat(invoke(registry, tooLarge.definition(), ALPHA_API).reasonCode()).isEqualTo("output.tooLarge");
    }

    @Test
    void emptyResultIsNotFound() {
        var outcome = invoke(registry(handler()), SERVICE_TOOL, ALPHA_API);
        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.NOT_FOUND);
    }

    @Test
    void crossProjectOutputIsDiscarded() {
        var beta = new EvidencePassage(new EvidenceRef("kb-beta", "v1", new ProjectId("beta"),
                Classification.INTERNAL, NOW, "perm:beta-members"), "beta secret");
        var outcome = invoke(registry(handler(passage("kb-1", "alpha"), beta)), SERVICE_TOOL, ALPHA_API);

        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.FAILED);
        assertThat(outcome.reasonCode()).isEqualTo("output.projectMismatch");
        assertThat(outcome.passages()).isEmpty();
    }

    @Test
    void evidenceIsReauthorizedPerSourceBeforeRelease() {
        policy.deny(PolicyAction.READ_EVIDENCE, "kb-restricted");
        var mixed = invoke(registry(handler(passage("kb-1", "open"), passage("kb-restricted", "classified"))),
                SERVICE_TOOL, ALPHA_API);
        var onlyRestricted = invoke(registry(handler(passage("kb-restricted", "classified"))),
                SERVICE_TOOL, ALPHA_API);

        assertThat(mixed.passages()).extracting(EvidencePassage::text).containsExactly("open");
        assertThat(mixed.toString()).doesNotContain("classified");
        assertThat(onlyRestricted.status()).isEqualTo(ToolOutcomeStatus.NOT_FOUND);
    }

    @Test
    void handlerTimeoutIsDeadlineExceeded() {
        var slow = definition("slowTool", ToolRisk.READ, Duration.ofMillis(100), 0, 1, 10);
        var tool = new FakeHandler(slow, attempt -> new CompletableFuture<>());

        var outcome = invoke(registry(tool), slow, ALPHA_API);

        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.DEADLINE_EXCEEDED);
        assertThat(outcome.reasonCode()).isEqualTo("tool.timeout");
    }

    @Test
    void portFailuresMapToStructuredOutcomesWithoutRetry() {
        for (var expected : Map.of(
                ErrorCode.AUTHENTICATION_REQUIRED, ToolOutcomeStatus.AUTHENTICATION_REQUIRED,
                ErrorCode.DENIED, ToolOutcomeStatus.DENIED,
                ErrorCode.CANCELLED, ToolOutcomeStatus.CANCELLED,
                ErrorCode.CONFLICT, ToolOutcomeStatus.FAILED).entrySet()) {
            var tool = new FakeHandler(SERVICE_TOOL, attempt -> failure(expected.getKey()));
            assertThat(invoke(registry(tool), SERVICE_TOOL, ALPHA_API).status()).isEqualTo(expected.getValue());
            assertThat(tool.calls).hasValue(1);
        }
    }

    @Test
    void unavailableDependencyIsRetriedWithinTheConfiguredLimit() {
        var recovering = new FakeHandler(SERVICE_TOOL, attempt -> attempt == 1
                ? failure(ErrorCode.DEPENDENCY_UNAVAILABLE)
                : passages(passage("kb-1", "recovered")));
        var down = new FakeHandler(definition("downTool", ToolRisk.READ, Duration.ofSeconds(1), 2, 1, 100),
                attempt -> failure(ErrorCode.DEPENDENCY_UNAVAILABLE));
        var registry = registry(recovering, down);

        assertThat(invoke(registry, SERVICE_TOOL, ALPHA_API).status()).isEqualTo(ToolOutcomeStatus.SUCCEEDED);
        assertThat(recovering.calls).hasValue(2);
        assertThat(invoke(registry, down.definition(), ALPHA_API).status()).isEqualTo(ToolOutcomeStatus.UNAVAILABLE);
        assertThat(down.calls).hasValue(3);
    }

    @Test
    void unexpectedAdapterExceptionsDoNotLeakMessages() {
        var tool = new FakeHandler(SERVICE_TOOL, attempt -> {
            throw new IllegalStateException("token=abc123 at db-host");
        });

        var outcome = invoke(registry(tool), SERVICE_TOOL, ALPHA_API);

        assertThat(outcome.status()).isEqualTo(ToolOutcomeStatus.FAILED);
        assertThat(outcome.toString()).doesNotContain("abc123", "db-host");
    }

    @Test
    void enabledRisksAreRequired() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(handler()), EnumSet.noneOf(ToolRisk.class), policy, CLOCK))
                .isInstanceOf(ContractViolationException.class);
    }
}
