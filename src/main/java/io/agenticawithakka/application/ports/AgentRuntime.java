package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ExecutionId;
import io.agenticawithakka.domain.contracts.TaskEnvelope;
import io.agenticawithakka.domain.contracts.TaskResult;
import java.util.concurrent.CompletionStage;

/**
 * Executes delegated agent tasks. Implementations may be in-process or Akka-based; callers must not
 * depend on which. Implementations enforce the envelope deadline and budget, never block caller
 * threads, and must not let late results overwrite a cancelled task.
 */
public interface AgentRuntime {
    AdapterCapabilities capabilities();

    /** Completes with a typed result, including explicit partial or failed results. */
    CompletionStage<TaskResult> submit(TaskEnvelope envelope);

    /** Idempotently requests cancellation; completes {@code true} if the execution was active. */
    CompletionStage<Boolean> cancel(ExecutionId executionId);
}
