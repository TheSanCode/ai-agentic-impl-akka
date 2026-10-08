package io.agenticawithakka.tools;

import io.agenticawithakka.domain.contracts.AgentRole;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ExecutionId;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.domain.contracts.TaskEnvelope;
import io.agenticawithakka.domain.contracts.TaskId;
import java.time.Instant;

/**
 * Trusted context for a tool call, taken from the validated task envelope and never from model
 * output (TOL-02, DEL-06). Identity, project and deadline cannot be changed by tool arguments.
 */
public record ToolInvocationContext(
        ExecutionId executionId,
        TaskId taskId,
        ProjectId projectId,
        IdentityContextRef identityContextRef,
        AgentRole agentRole,
        Instant deadline) {

    public ToolInvocationContext {
        ContractValidation.required(executionId, "executionId");
        ContractValidation.required(taskId, "taskId");
        ContractValidation.required(projectId, "projectId");
        ContractValidation.required(identityContextRef, "identityContextRef");
        ContractValidation.required(agentRole, "agentRole");
        ContractValidation.required(deadline, "deadline");
    }

    public static ToolInvocationContext from(TaskEnvelope envelope) {
        ContractValidation.required(envelope, "envelope");
        return new ToolInvocationContext(envelope.executionId(), envelope.taskId(), envelope.projectId(),
                envelope.identityContextRef(), envelope.agentRole(), envelope.deadline());
    }
}
