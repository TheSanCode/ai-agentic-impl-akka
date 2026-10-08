package io.agenticawithakka.domain.contracts;

/** Validated request to run a registered tool; policy is checked before execution. */
public record ToolRequest(
        ExecutionId executionId,
        TaskId taskId,
        ToolRef tool,
        ToolArguments validatedArguments,
        IdempotencyKey idempotencyKey) {

    public ToolRequest {
        ContractValidation.required(executionId, "executionId");
        ContractValidation.required(taskId, "taskId");
        ContractValidation.required(tool, "tool");
        ContractValidation.required(validatedArguments, "validatedArguments");
        ContractValidation.required(idempotencyKey, "idempotencyKey");
    }
}
