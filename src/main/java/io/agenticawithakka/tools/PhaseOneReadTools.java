package io.agenticawithakka.tools;

import io.agenticawithakka.application.ports.DelegatedTokenProvider;
import io.agenticawithakka.application.ports.PolicyDecisionService;
import io.agenticawithakka.application.ports.SearchGateway;
import io.agenticawithakka.application.ports.SourceConnector;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 1 tool catalogue: exactly searchKnowledge, queryMockLogs and inspectMockHealth, with only
 * {@link ToolRisk#READ} enabled. Write or draft tools cannot be registered in this installation.
 */
public final class PhaseOneReadTools {
    public static final Set<ToolRisk> ENABLED_RISKS = Set.copyOf(EnumSet.of(ToolRisk.READ));

    private PhaseOneReadTools() {
    }

    public static ToolRegistry registry(
            SearchGateway search,
            SourceConnector logs,
            DelegationTarget logsTarget,
            SourceConnector health,
            DelegationTarget healthTarget,
            DelegatedTokenProvider tokens,
            PolicyDecisionService policy,
            Clock clock) {
        List<ToolHandler> handlers = List.of(
                new SearchKnowledgeTool(search),
                new QueryMockLogsTool(logs, tokens, logsTarget, clock),
                new InspectMockHealthTool(health, tokens, healthTarget, clock));
        return new ToolRegistry(handlers, ENABLED_RISKS, policy, clock);
    }
}
