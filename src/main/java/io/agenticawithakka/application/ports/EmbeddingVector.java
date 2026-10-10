package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import java.util.List;

/** A vector tied to the exact embedding model and dimension that produced it. */
public record EmbeddingVector(String modelId, List<Float> values) {
    public static final int MAX_DIMENSIONS = 16_000;

    public EmbeddingVector {
        ContractValidation.matches(modelId, "embedding.modelId", ContractValidation.REFERENCE);
        values = ContractValidation.list(values, "embedding.values", MAX_DIMENSIONS);
        if (values.isEmpty()) {
            throw new ContractViolationException("embedding.values", "must contain at least one dimension");
        }
        boolean nonZero = false;
        for (Float value : values) {
            if (!Float.isFinite(value)) {
                throw new ContractViolationException("embedding.values", "must contain only finite values");
            }
            nonZero |= value != 0.0f;
        }
        if (!nonZero) {
            throw new ContractViolationException("embedding.values", "must not be a zero vector");
        }
    }

    public int dimensions() {
        return values.size();
    }

    public String pgvectorLiteral() {
        return values.stream()
                .map(value -> Float.toString(value))
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
