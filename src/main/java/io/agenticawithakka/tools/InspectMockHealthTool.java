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

/** Read tool: reports the mock health status of one service using delegated access. */
public final class InspectMockHealthTool implements ToolHandler {
    public static final ToolRef REF = new ToolRef("inspectMockHealth", 1);
    static final String OPERATION = "inspectHealth";

    public static final ToolDefinition DEFINITION = new ToolDefinition(
            REF,
            "Report the current health checks for one service in the current project.",
            ToolRisk.READ,
            Set.of(AgentRole.INVESTIGATION),
            List.of(ArgumentSpec.reference("service", true, true)),
            null,
            Duration.ofSeconds(5),
            1,
            5,
            5_000);

    private final DelegatedSourceAccess access;

    public InspectMockHealthTool(
            SourceConnector health, DelegatedTokenProvider tokens, DelegationTarget target, Clock clock) {
        this.access = new DelegatedSourceAccess(health, tokens, target, clock);
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
