package io.agenticawithakka.tools;

import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.ToolArguments;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Executable implementation of one registered tool. Only {@link ToolRegistry} calls it, after role,
 * argument and policy checks pass. Implementations take identity and project solely from the
 * context, signal failures with {@code PortException}, and return an empty list when nothing matched.
 */
public interface ToolHandler {
    ToolDefinition definition();

    CompletionStage<List<EvidencePassage>> execute(ToolInvocationContext context, ToolArguments arguments);
}
