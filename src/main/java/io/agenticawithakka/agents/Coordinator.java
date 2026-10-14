package io.agenticawithakka.agents;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.Finding;
import io.agenticawithakka.domain.contracts.FindingKind;
import io.agenticawithakka.domain.contracts.TaskEnvelope;
import io.agenticawithakka.domain.contracts.TaskResult;
import io.agenticawithakka.domain.contracts.TaskStatus;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/** Coordinates the local Investigation role and converts its bounded tool run into a task result. */
public final class Coordinator {
    private final InvestigationAgent investigation;
    private final Clock clock;

    public Coordinator(InvestigationAgent investigation, Clock clock) {
        this.investigation = ContractValidation.required(investigation, "investigation");
        this.clock = ContractValidation.required(clock, "clock");
    }

    public CompletionStage<Outcome> investigate(
            TaskEnvelope envelope,
            InvestigationAgent.Cursor cursor,
            BooleanSupplier cancelled,
            BooleanSupplier reserveStep) {
        return investigation.investigate(envelope, cursor, cancelled, reserveStep)
                .thenApply(run -> {
                    if (run.awaitingAuthentication()) {
                        return new Outcome(run.cursor(), true, null);
                    }
                    if (run.cancelled()) {
                        return new Outcome(run.cursor(), false, new TaskResult(
                                envelope.taskId(), TaskStatus.CANCELLED, List.of(), List.of(),
                                List.of("Investigation cancelled before further tools were called."),
                                ErrorCode.CANCELLED, clock.instant()));
                    }
                    if (run.budgetExhausted()) {
                        return new Outcome(run.cursor(), false, new TaskResult(
                                envelope.taskId(), TaskStatus.FAILED, List.of(), List.of(),
                                List.of("The investigation reached its step or deadline limit."),
                                envelope.expiredAt(clock.instant())
                                        ? ErrorCode.DEADLINE_EXCEEDED
                                        : ErrorCode.BUDGET_EXHAUSTED,
                                clock.instant()));
                    }

                    var passages = run.cursor().passages();
                    var references = passages.stream().map(passage -> passage.ref()).distinct().toList();
                    var findings = passages.stream()
                            .map(passage -> new Finding(
                                    FindingKind.FACT,
                                    "Authorized evidence was retrieved from " + passage.ref().sourceId() + ".",
                                    List.of(passage.ref())))
                            .toList();
                    var limitations = run.cursor().limitations().stream().distinct().toList();
                    if (passages.isEmpty()) {
                        return new Outcome(run.cursor(), false, new TaskResult(
                                envelope.taskId(), TaskStatus.FAILED, List.of(), List.of(),
                                limitations.isEmpty() ? List.of("No authorized evidence was found.") : limitations,
                                ErrorCode.DEPENDENCY_UNAVAILABLE, clock.instant()));
                    }
                    boolean partial = !limitations.isEmpty();
                    var result = new TaskResult(
                            envelope.taskId(),
                            partial ? TaskStatus.PARTIAL : TaskStatus.SUCCEEDED,
                            references,
                            findings,
                            limitations,
                            partial ? ErrorCode.DEPENDENCY_UNAVAILABLE : null,
                            clock.instant());
                    return new Outcome(run.cursor(), false, result);
                });
    }

    public record Outcome(InvestigationAgent.Cursor cursor, boolean awaitingAuthentication, TaskResult result) {}
}
