package io.agenticawithakka.domain.contracts;

import java.util.UUID;

/** Server-generated identifier. */
public record ExecutionId(UUID value) {
    public ExecutionId {
        ContractValidation.required(value, "executionId");
    }

    public static ExecutionId random() {
        return new ExecutionId(UUID.randomUUID());
    }
}
