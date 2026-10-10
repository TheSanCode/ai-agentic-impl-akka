package io.agenticawithakka.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agenticawithakka.domain.contracts.CorrelationId;
import io.agenticawithakka.domain.contracts.ExecutionId;
import io.agenticawithakka.domain.contracts.ExecutionStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExecutionTraceTest {
    @Test
    void evictsOldestEventsWhenCapacityIsReached() {
        var trace = new ExecutionTrace(2);
        var execution = ExecutionId.random();
        var correlation = CorrelationId.random();
        var now = Instant.parse("2026-10-10T12:00:00Z");

        trace.record(execution, correlation, now, ExecutionTrace.EventType.ADMITTED, ExecutionStatus.QUEUED, 0);
        trace.record(execution, correlation, now.plusSeconds(1), ExecutionTrace.EventType.STARTED,
                ExecutionStatus.RUNNING, 0);
        trace.record(execution, correlation, now.plusSeconds(2), ExecutionTrace.EventType.SUCCEEDED,
                ExecutionStatus.SUCCEEDED, 1);

        assertThat(trace.eventsFor(execution.value()))
                .extracting(event -> event.type())
                .containsExactly(ExecutionTrace.EventType.STARTED, ExecutionTrace.EventType.SUCCEEDED);
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new ExecutionTrace(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("trace capacity must be positive");
    }
}
