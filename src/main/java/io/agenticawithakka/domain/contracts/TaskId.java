package io.agenticawithakka.domain.contracts;

import java.util.UUID;

/** Server-generated identifier. */
public record TaskId(UUID value) {
    public TaskId {
        ContractValidation.required(value, "taskId");
    }

    public static TaskId random() {
        return new TaskId(UUID.randomUUID());
    }
}
