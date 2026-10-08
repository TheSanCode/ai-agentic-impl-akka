package io.agenticawithakka.domain.contracts;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Valid baseline values; tests mutate one field at a time. */
public final class ContractFixtures {
    public static final Instant NOW = Instant.parse("2026-10-08T05:00:00Z");
    public static final ProjectId ALPHA = new ProjectId("alpha");

    private ContractFixtures() {
    }

    public static Budget budget() {
        return new Budget(8, Duration.ofMinutes(2), 16_000, 0);
    }

    public static TaskEnvelope envelope() {
        return new TaskEnvelope(TaskEnvelope.SCHEMA_VERSION, ExecutionId.random(), TaskId.random(),
                CorrelationId.random(), ALPHA, new IdentityContextRef("ictx_0123456789abcdef"),
                NOW.plusSeconds(120), AgentRole.INVESTIGATION,
                new TaskInput("Investigate connection timeouts in the Alpha service."),
                new ReplyRoute("coordinator:alpha:1"), budget());
    }

    public static EvidenceRef evidence(String sourceId) {
        return new EvidenceRef(sourceId, "v1", ALPHA, Classification.INTERNAL, NOW, "perm:alpha-members");
    }

    public static TaskResult success(List<EvidenceRef> refs, List<Finding> findings) {
        return new TaskResult(TaskId.random(), TaskStatus.SUCCEEDED, refs, findings, List.of(), null, NOW);
    }

    public static ToolRequest toolRequest(Map<String, String> arguments) {
        return new ToolRequest(ExecutionId.random(), TaskId.random(), new ToolRef("queryMockLogs", 1),
                new ToolArguments(arguments), new IdempotencyKey("exec-1:task-1:queryMockLogs:0001"));
    }
}
