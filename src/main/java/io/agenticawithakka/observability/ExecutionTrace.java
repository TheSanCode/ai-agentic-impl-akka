package io.agenticawithakka.observability;

import io.agenticawithakka.domain.contracts.CorrelationId;
import io.agenticawithakka.domain.contracts.ExecutionId;
import io.agenticawithakka.domain.contracts.ExecutionStatus;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded, process-local lifecycle events containing only allowlisted metadata. */
public final class ExecutionTrace {
    public static final int MAX_RETAINED_EVENTS = 10_000;

    private final int capacity;
    private final ArrayDeque<Event> events = new ArrayDeque<>();

    public ExecutionTrace() {
        this(MAX_RETAINED_EVENTS);
    }

    ExecutionTrace(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("trace capacity must be positive");
        }
        this.capacity = capacity;
    }

    public synchronized void record(
            ExecutionId executionId,
            CorrelationId correlationId,
            Instant occurredAt,
            EventType type,
            ExecutionStatus status,
            int steps) {
        var event = new Event(
                executionId.value(),
                correlationId.value(),
                occurredAt,
                type,
                status,
                steps);
        if (events.size() == capacity) {
            events.removeFirst();
        }
        events.addLast(event);
    }

    public synchronized List<Event> eventsFor(UUID executionId) {
        Objects.requireNonNull(executionId, "executionId");
        return events.stream().filter(event -> event.executionId().equals(executionId)).toList();
    }

    public enum EventType {
        ADMITTED,
        STARTED,
        STEP_RESERVED,
        AUTHENTICATION_REQUIRED,
        RESUMED,
        SUCCEEDED,
        PARTIAL,
        FAILED,
        CANCELLED
    }

    public record Event(
            UUID executionId,
            UUID correlationId,
            Instant occurredAt,
            EventType type,
            ExecutionStatus status,
            int steps) {
        public Event {
            Objects.requireNonNull(executionId, "executionId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(status, "status");
            if (steps < 0) {
                throw new IllegalArgumentException("trace steps must not be negative");
            }
        }
    }
}
