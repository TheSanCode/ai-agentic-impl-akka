package io.agenticawithakka.tools;

import io.agenticawithakka.application.ports.DelegatedTokenProvider;
import io.agenticawithakka.application.ports.SourceConnector;
import io.agenticawithakka.domain.contracts.AgentRole;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.ToolArguments;
import io.agenticawithakka.domain.contracts.ToolRef;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Read tool: queries mock service logs within a bounded time window using delegated access. */
public final class QueryMockLogsTool implements ToolHandler {
    public static final ToolRef REF = new ToolRef("queryMockLogs", 1);
    static final String OPERATION = "queryLogs";

    public static final ToolDefinition DEFINITION = new ToolDefinition(
            REF,
            "Query log lines for one service in the current project between two ISO-8601 instants.",
            ToolRisk.READ,
            Set.of(AgentRole.INVESTIGATION),
            List.of(
                    ArgumentSpec.reference("service", true, true),
                    ArgumentSpec.instant("from", true),
                    ArgumentSpec.instant("to", true),
                    ArgumentSpec.text("contains", false, 200)),
            new TimeWindow("from", "to", Duration.ofHours(6), Duration.ofDays(30)),
            Duration.ofSeconds(10),
            1,
            20,
            20_000);

    private final DelegatedSourceAccess access;

    public QueryMockLogsTool(
            SourceConnector logs, DelegatedTokenProvider tokens, DelegationTarget target, Clock clock) {
        this.access = new DelegatedSourceAccess(logs, tokens, target, clock);
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public CompletionStage<List<EvidencePassage>> execute(ToolInvocationContext context, ToolArguments arguments) {
        return access.read(context, OPERATION, arguments, DEFINITION.maxOutputPassages());
    }
}
