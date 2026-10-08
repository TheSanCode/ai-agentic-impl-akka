package io.agenticawithakka.domain.contracts;

import static io.agenticawithakka.domain.contracts.ContractFixtures.ALPHA;
import static io.agenticawithakka.domain.contracts.ContractFixtures.NOW;
import static io.agenticawithakka.domain.contracts.ContractFixtures.envelope;
import static io.agenticawithakka.domain.contracts.ContractFixtures.evidence;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ContractValidationTest {

    @Test
    void validEnvelopeIsAccepted() {
        var envelope = envelope();
        assertThat(envelope.schemaVersion()).isEqualTo(1);
        assertThat(envelope.expiredAt(NOW)).isFalse();
        assertThat(envelope.expiredAt(envelope.deadline())).isTrue();
    }

    static Stream<Arguments> malformedEnvelopes() {
        var e = envelope();
        return Stream.of(
                Arguments.of("schemaVersion", (Executable) () -> new TaskEnvelope(2, e.executionId(), e.taskId(),
                        e.correlationId(), e.projectId(), e.identityContextRef(), e.deadline(), e.agentRole(),
                        e.input(), e.replyRoute(), e.budget())),
                Arguments.of("identityContextRef", (Executable) () -> new TaskEnvelope(1, e.executionId(),
                        e.taskId(), e.correlationId(), e.projectId(), null, e.deadline(), e.agentRole(), e.input(),
                        e.replyRoute(), e.budget())),
                Arguments.of("deadline", (Executable) () -> new TaskEnvelope(1, e.executionId(), e.taskId(),
                        e.correlationId(), e.projectId(), e.identityContextRef(), null, e.agentRole(), e.input(),
                        e.replyRoute(), e.budget())),
                Arguments.of("budget", (Executable) () -> new TaskEnvelope(1, e.executionId(), e.taskId(),
                        e.correlationId(), e.projectId(), e.identityContextRef(), e.deadline(), e.agentRole(),
                        e.input(), e.replyRoute(), null)),
                Arguments.of("projectId", (Executable) () -> new ProjectId("Alpha Project")),
                Arguments.of("projectId", (Executable) () -> new ProjectId("../beta")),
                Arguments.of("identityContextRef", (Executable) () -> new IdentityContextRef("short")),
                Arguments.of("identityContextRef", (Executable) () -> new IdentityContextRef("Bearer eyJ.abc.def-ghijk")),
                Arguments.of("replyRoute", (Executable) () -> new ReplyRoute("akka://system/user/a b")),
                Arguments.of("input.instruction", (Executable) () -> new TaskInput("   ")),
                Arguments.of("input.instruction", (Executable) () -> new TaskInput("x".repeat(TaskInput.MAX_LENGTH + 1))),
                Arguments.of("input.instruction", (Executable) () -> new TaskInput("ok\u0000hidden")),
                Arguments.of("budget.maxSteps", (Executable) () -> new Budget(0, Duration.ofSeconds(1), 1, 0)),
                Arguments.of("budget.maxElapsed", (Executable) () -> new Budget(1, Duration.ZERO, 1, 0)),
                Arguments.of("budget.maxTokens", (Executable) () -> new Budget(1, Duration.ofSeconds(1), -5, 0)),
                Arguments.of("budget.maxCostMicros", (Executable) () -> new Budget(1, Duration.ofSeconds(1), 1, -1)));
    }

    @ParameterizedTest(name = "[{index}] rejects {0}")
    @MethodSource("malformedEnvelopes")
    void malformedEnvelopeFieldsAreRejected(String field, Executable construction) {
        assertThatThrownBy(construction::execute)
                .isInstanceOf(ContractViolationException.class)
                .satisfies(ex -> assertThat(((ContractViolationException) ex).field()).isEqualTo(field));
    }

    @Test
    void violationMessagesDoNotEchoRejectedValues() {
        var secretLooking = "Bearer eyJhbGciOi.secret-token-value";
        assertThatThrownBy(() -> new IdentityContextRef(secretLooking))
                .hasMessageNotContaining("secret-token-value")
                .hasMessage("identityContextRef: has an invalid format");
    }

    static Stream<Arguments> malformedToolRequests() {
        var tooMany = new HashMap<String, String>();
        IntStream.range(0, ToolArguments.MAX_ENTRIES + 1).forEach(i -> tooMany.put("arg" + i, "v"));
        var nullValue = new HashMap<String, String>();
        nullValue.put("since", null);
        return Stream.of(
                Arguments.of("tool.id", (Executable) () -> new ToolRef("rm -rf", 1)),
                Arguments.of("tool.id", (Executable) () -> new ToolRef("QueryMockLogs", 1)),
                Arguments.of("tool.version", (Executable) () -> new ToolRef("queryMockLogs", 0)),
                Arguments.of("arguments.key", (Executable) () -> new ToolArguments(Map.of("$where", "1=1"))),
                Arguments.of("arguments.value", (Executable) () -> new ToolArguments(Map.of("query", ""))),
                Arguments.of("arguments.value", (Executable) () -> new ToolArguments(
                        Map.of("query", "x".repeat(ToolArguments.MAX_VALUE_LENGTH + 1)))),
                Arguments.of("arguments", (Executable) () -> new ToolArguments(tooMany)),
                Arguments.of("arguments", (Executable) () -> new ToolArguments(nullValue)),
                Arguments.of("idempotencyKey", (Executable) () -> new IdempotencyKey("short")),
                Arguments.of("validatedArguments", (Executable) () -> new ToolRequest(ExecutionId.random(),
                        TaskId.random(), new ToolRef("queryMockLogs", 1), null,
                        new IdempotencyKey("exec-1:task-1:queryMockLogs:0001"))));
    }

    @ParameterizedTest(name = "[{index}] rejects {0}")
    @MethodSource("malformedToolRequests")
    void malformedToolRequestsAreRejected(String field, Executable construction) {
        assertThatThrownBy(construction::execute)
                .isInstanceOf(ContractViolationException.class)
                .satisfies(ex -> assertThat(((ContractViolationException) ex).field()).isEqualTo(field));
    }

    @Test
    void validToolRequestArgumentsAreImmutableCopies() {
        var source = new HashMap<String, String>();
        source.put("service", "alpha-api");
        var request = ContractFixtures.toolRequest(source);
        source.put("injected", "value");
        assertThat(request.validatedArguments().values()).containsOnlyKeys("service");
        assertThatThrownBy(() -> request.validatedArguments().values().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void factsRequireEvidenceButHypothesesMayNot() {
        assertThatThrownBy(() -> new Finding(FindingKind.FACT, "Pool exhausted.", List.of()))
                .isInstanceOf(ContractViolationException.class)
                .hasMessageContaining("facts require evidence");
        assertThat(new Finding(FindingKind.HYPOTHESIS, "Pool may be undersized.", List.of()).evidenceRefs())
                .isEmpty();
    }

    @Test
    void findingsMayOnlyCiteEvidenceIncludedInTheResult() {
        var cited = evidence("runbook-timeouts");
        var fact = new Finding(FindingKind.FACT, "Timeouts began at 04:10.", List.of(cited));
        assertThat(ContractFixtures.success(List.of(cited), List.of(fact)).findings()).hasSize(1);
        assertThatThrownBy(() -> ContractFixtures.success(List.of(evidence("other")), List.of(fact)))
                .isInstanceOf(ContractViolationException.class)
                .hasMessageContaining("cite evidence absent");
    }

    static Stream<Arguments> inconsistentResults() {
        var none = List.<String>of();
        return Stream.of(
                Arguments.of(TaskStatus.SUCCEEDED, ErrorCode.DENIED, none),
                Arguments.of(TaskStatus.FAILED, null, none),
                Arguments.of(TaskStatus.FAILED, ErrorCode.CANCELLED, none),
                Arguments.of(TaskStatus.PARTIAL, ErrorCode.DEADLINE_EXCEEDED, none),
                Arguments.of(TaskStatus.PARTIAL, null, List.of("Logs after 04:30 unavailable.")),
                Arguments.of(TaskStatus.CANCELLED, null, none),
                Arguments.of(TaskStatus.CANCELLED, ErrorCode.DENIED, none));
    }

    @ParameterizedTest(name = "[{index}] {0} with {1}")
    @MethodSource("inconsistentResults")
    void statusAndErrorCodeMustAgree(TaskStatus status, ErrorCode code, List<String> limitations) {
        assertThatThrownBy(() -> new TaskResult(TaskId.random(), status, List.of(), List.of(), limitations, code, NOW))
                .isInstanceOf(ContractViolationException.class);
    }

    @Test
    void explicitPartialAndCancelledResultsAreAccepted() {
        var partial = new TaskResult(TaskId.random(), TaskStatus.PARTIAL, List.of(), List.of(),
                List.of("Log source unavailable; runbook evidence only."), ErrorCode.DEPENDENCY_UNAVAILABLE, NOW);
        var cancelled = new TaskResult(TaskId.random(), TaskStatus.CANCELLED, List.of(), List.of(), List.of(),
                ErrorCode.CANCELLED, NOW);
        assertThat(partial.limitations()).hasSize(1);
        assertThat(cancelled.errorCode()).isEqualTo(ErrorCode.CANCELLED);
    }

    @Test
    void resultListsAreBoundedAndNullFree() {
        var tooMany = new ArrayList<EvidenceRef>();
        IntStream.range(0, TaskResult.MAX_EVIDENCE + 1).forEach(i -> tooMany.add(evidence("s" + i)));
        assertThatThrownBy(() -> ContractFixtures.success(tooMany, List.of()))
                .isInstanceOf(ContractViolationException.class);
        var withNull = new ArrayList<EvidenceRef>();
        withNull.add(null);
        assertThatThrownBy(() -> ContractFixtures.success(withNull, List.of()))
                .isInstanceOf(ContractViolationException.class);
    }

    @Test
    void evidenceRequiresTraceableSourceMetadata() {
        assertThatThrownBy(() -> new EvidenceRef("doc 1", "v1", ALPHA, Classification.INTERNAL, NOW, "perm"))
                .isInstanceOf(ContractViolationException.class);
        assertThatThrownBy(() -> new EvidenceRef("doc-1", "v1", ALPHA, null, NOW, "perm"))
                .isInstanceOf(ContractViolationException.class);
        assertThatThrownBy(() -> new EvidenceRef("doc-1", "v1", ALPHA, Classification.INTERNAL, NOW, null))
                .isInstanceOf(ContractViolationException.class);
    }

    @Test
    void terminalExecutionStatesNeverTransition() {
        for (var terminal : List.of(ExecutionStatus.SUCCEEDED, ExecutionStatus.FAILED, ExecutionStatus.CANCELLED)) {
            assertThat(terminal.isTerminal()).isTrue();
            for (var next : ExecutionStatus.values()) {
                assertThat(terminal.canTransitionTo(next)).as("%s -> %s", terminal, next).isFalse();
            }
        }
        assertThat(ExecutionStatus.QUEUED.canTransitionTo(ExecutionStatus.RUNNING)).isTrue();
        assertThat(ExecutionStatus.QUEUED.canTransitionTo(ExecutionStatus.SUCCEEDED)).isFalse();
        assertThat(ExecutionStatus.RUNNING.canTransitionTo(ExecutionStatus.AWAITING_AUTHENTICATION)).isTrue();
        assertThat(ExecutionStatus.AWAITING_AUTHENTICATION.canTransitionTo(ExecutionStatus.RUNNING)).isTrue();
        assertThat(ExecutionStatus.AWAITING_AUTHENTICATION.canTransitionTo(ExecutionStatus.SUCCEEDED)).isFalse();
    }
}
