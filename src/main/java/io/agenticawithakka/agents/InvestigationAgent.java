package io.agenticawithakka.agents;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.IdempotencyKey;
import io.agenticawithakka.domain.contracts.ToolArguments;
import io.agenticawithakka.domain.contracts.ToolRef;
import io.agenticawithakka.domain.contracts.ToolRequest;
import io.agenticawithakka.domain.contracts.TaskEnvelope;
import io.agenticawithakka.tools.QueryMockLogsTool;
import io.agenticawithakka.tools.SearchKnowledgeTool;
import io.agenticawithakka.tools.ToolInvocationContext;
import io.agenticawithakka.tools.ToolOutcome;
import io.agenticawithakka.tools.ToolOutcomeStatus;
import io.agenticawithakka.tools.ToolRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/** Bounded, deterministic read-only Investigation role built on the authorized tool registry. */
public final class InvestigationAgent {
    private final ToolRegistry tools;
    private final Clock clock;

    public InvestigationAgent(ToolRegistry tools, Clock clock) {
        this.tools = ContractValidation.required(tools, "tools");
        this.clock = ContractValidation.required(clock, "clock");
    }

    public CompletionStage<Run> investigate(
            TaskEnvelope envelope, Cursor cursor, BooleanSupplier cancelled, BooleanSupplier reserveStep) {
        return runNext(envelope, cursor, cancelled, reserveStep);
    }

    private CompletionStage<Run> runNext(
            TaskEnvelope envelope, Cursor cursor, BooleanSupplier cancelled, BooleanSupplier reserveStep) {
        if (cancelled.getAsBoolean()) {
            return done(new Run(cursor, false, true, false));
        }
        if (envelope.expiredAt(clock.instant())) {
            return done(new Run(cursor, false, false, true));
        }
        if (cursor.nextToolIndex() == 2) {
            return done(new Run(cursor, false, false, false));
        }
        if (!reserveStep.getAsBoolean()) {
            return done(new Run(cursor, false, false, true));
        }

        int attemptedStep = cursor.steps() + 1;
        ToolRequest request = request(envelope, cursor.nextToolIndex(), attemptedStep, clock.instant());
        CompletionStage<ToolOutcome> invocation;
        try {
            invocation = tools.invoke(ToolInvocationContext.from(envelope), request);
            if (invocation == null) {
                invocation = CompletableFuture.failedFuture(new IllegalStateException("tool returned no outcome"));
            }
        } catch (RuntimeException exception) {
            invocation = CompletableFuture.failedFuture(exception);
        }
        int currentTool = cursor.nextToolIndex();
        Cursor attempted = new Cursor(currentTool, attemptedStep, cursor.passages(), cursor.limitations());
        return invocation.handle((outcome, failure) -> {
            if (failure != null || outcome == null) {
                return new ToolResult(null, "tool.unavailable", false);
            }
            if (outcome.status() == ToolOutcomeStatus.AUTHENTICATION_REQUIRED) {
                return new ToolResult(outcome, null, true);
            }
            if (outcome.status() == ToolOutcomeStatus.CANCELLED) {
                return new ToolResult(outcome, null, false);
            }
            if (outcome.status() != ToolOutcomeStatus.SUCCEEDED) {
                return new ToolResult(outcome, outcome.reasonCode(), false);
            }
            return new ToolResult(outcome, null, false);
        }).thenCompose(result -> {
            if (result.awaitingAuthentication()) {
                return done(new Run(attempted, true, false, false));
            }
            if (result.outcome() != null && result.outcome().status() == ToolOutcomeStatus.CANCELLED) {
                return done(new Run(attempted, false, true, false));
            }
            List<EvidencePassage> passages = new ArrayList<>(cursor.passages());
            List<String> limitations = new ArrayList<>(cursor.limitations());
            if (result.outcome() != null && result.outcome().status() == ToolOutcomeStatus.SUCCEEDED) {
                passages.addAll(result.outcome().passages());
            }
            if (result.reasonCode() != null) {
                limitations.add(result.reasonCode());
            }
            Cursor next = new Cursor(currentTool + 1, attemptedStep, deduplicate(passages), limitations);
            return runNext(envelope, next, cancelled, reserveStep);
        });
    }

    private static ToolRequest request(TaskEnvelope envelope, int index, int step, java.time.Instant now) {
        ToolRef tool;
        Map<String, String> arguments;
        if (index == 0) {
            tool = SearchKnowledgeTool.REF;
            String query = envelope.input().instruction();
            arguments = Map.of("query", query.substring(0, Math.min(query.length(), 500)), "maxResults", "5");
        } else {
            tool = QueryMockLogsTool.REF;
            var end = now.isBefore(envelope.deadline()) ? now : envelope.deadline();
            arguments = Map.of(
                    "service", "billing",
                    "from", end.minusSeconds(3_600).toString(),
                    "to", end.toString());
        }
        String key = envelope.executionId().value() + ":" + envelope.taskId().value()
                + ":" + tool.id() + ":" + String.format("%04d", step);
        return new ToolRequest(envelope.executionId(), envelope.taskId(), tool,
                new ToolArguments(arguments), new IdempotencyKey(key));
    }

    private static List<EvidencePassage> deduplicate(List<EvidencePassage> passages) {
        var unique = new LinkedHashMap<io.agenticawithakka.domain.contracts.EvidenceRef, EvidencePassage>();
        passages.forEach(passage -> unique.putIfAbsent(passage.ref(), passage));
        return List.copyOf(unique.values());
    }

    private static <T> CompletionStage<T> done(T value) {
        return CompletableFuture.completedFuture(value);
    }

    public record Cursor(
            int nextToolIndex, int steps, List<EvidencePassage> passages, List<String> limitations) {
        public Cursor {
            if (nextToolIndex < 0 || nextToolIndex > 2 || steps < 0) {
                throw new IllegalArgumentException("cursor position or step count is invalid");
            }
            passages = List.copyOf(passages);
            limitations = List.copyOf(limitations);
        }

        public static Cursor initial() {
            return new Cursor(0, 0, List.of(), List.of());
        }
    }

    public record Run(Cursor cursor, boolean awaitingAuthentication, boolean cancelled, boolean budgetExhausted) {}

    private record ToolResult(ToolOutcome outcome, String reasonCode, boolean awaitingAuthentication) {}
}
