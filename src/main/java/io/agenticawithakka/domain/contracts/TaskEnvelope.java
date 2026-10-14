package io.agenticawithakka.domain.contracts;

import java.time.Instant;

/** Versioned unit of work delegated to an agent. */
public record TaskEnvelope(
        int schemaVersion,
        ExecutionId executionId,
        TaskId taskId,
        CorrelationId correlationId,
        ProjectId projectId,
        IdentityContextRef identityContextRef,
        Instant deadline,
        AgentRole agentRole,
        TaskInput input,
        ReplyRoute replyRoute,
        Budget budget) {

    public static final int SCHEMA_VERSION = 1;

    public TaskEnvelope {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new ContractViolationException("schemaVersion", "is not supported");
        }
        ContractValidation.required(executionId, "executionId");
        ContractValidation.required(taskId, "taskId");
        ContractValidation.required(correlationId, "correlationId");
        ContractValidation.required(projectId, "projectId");
        ContractValidation.required(identityContextRef, "identityContextRef");
        ContractValidation.required(deadline, "deadline");
        ContractValidation.required(agentRole, "agentRole");
        ContractValidation.required(input, "input");
        ContractValidation.required(replyRoute, "replyRoute");
        ContractValidation.required(budget, "budget");
    }

    public boolean expiredAt(Instant now) {
        return !now.isBefore(deadline);
    }
}
