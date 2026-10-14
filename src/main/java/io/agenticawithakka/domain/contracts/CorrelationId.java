package io.agenticawithakka.domain.contracts;

import java.util.UUID;

/** Server-generated identifier. */
public record CorrelationId(UUID value) {
    public CorrelationId {
        ContractValidation.required(value, "correlationId");
    }

    public static CorrelationId random() {
        return new CorrelationId(UUID.randomUUID());
    }
}
