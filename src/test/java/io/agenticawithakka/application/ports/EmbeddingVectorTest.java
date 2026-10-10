package io.agenticawithakka.application.ports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agenticawithakka.domain.contracts.ContractViolationException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingVectorTest {
    @Test
    void vectorRetainsModelAndDimensionAndProducesSqlSafeLiteral() {
        var vector = new EmbeddingVector("fixture-v1", List.of(1.25f, -0.5f));

        assertThat(vector.modelId()).isEqualTo("fixture-v1");
        assertThat(vector.dimensions()).isEqualTo(2);
        assertThat(vector.pgvectorLiteral()).isEqualTo("[1.25,-0.5]");
    }

    @Test
    void vectorsMustHaveFiniteNonZeroValuesAndAValidModelIdentifier() {
        assertThatThrownBy(() -> new EmbeddingVector("model with spaces", List.of(1.0f)))
                .isInstanceOf(ContractViolationException.class);
        assertThatThrownBy(() -> new EmbeddingVector("fixture-v1", List.of()))
                .isInstanceOf(ContractViolationException.class);
        assertThatThrownBy(() -> new EmbeddingVector("fixture-v1", List.of(0.0f, -0.0f)))
                .isInstanceOf(ContractViolationException.class);
        assertThatThrownBy(() -> new EmbeddingVector("fixture-v1", List.of(Float.POSITIVE_INFINITY)))
                .isInstanceOf(ContractViolationException.class);
        assertThatThrownBy(() -> new EmbeddingVector("fixture-v1", Arrays.asList(1.0f, null)))
                .isInstanceOf(ContractViolationException.class);
    }
}
