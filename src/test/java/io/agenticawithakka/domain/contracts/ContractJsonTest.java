package io.agenticawithakka.domain.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Contracts must keep their invariants when crossing a serialization boundary. */
class ContractJsonTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void envelopeRoundTripsThroughJson() {
        var envelope = ContractFixtures.envelope();
        var json = mapper.writeValueAsString(envelope);
        assertThat(mapper.readValue(json, TaskEnvelope.class)).isEqualTo(envelope);
    }

    @Test
    void deserializationEnforcesSchemaVersionAndFieldRules() {
        var tree = (ObjectNode) mapper.valueToTree(ContractFixtures.envelope());
        tree.put("schemaVersion", 99);
        assertThatThrownBy(() -> mapper.treeToValue(tree, TaskEnvelope.class))
                .hasRootCauseInstanceOf(ContractViolationException.class)
                .rootCause().hasMessage("schemaVersion: is not supported");

        var injected = (ObjectNode) mapper.valueToTree(ContractFixtures.envelope());
        ((ObjectNode) injected.get("projectId")).put("value", "beta; DROP");
        assertThatThrownBy(() -> mapper.treeToValue(injected, TaskEnvelope.class))
                .hasRootCauseInstanceOf(ContractViolationException.class);
    }
}
