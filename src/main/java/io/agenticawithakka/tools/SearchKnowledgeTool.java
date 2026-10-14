package io.agenticawithakka.tools;

import io.agenticawithakka.application.ports.SearchGateway;
import io.agenticawithakka.application.ports.SearchQuery;
import io.agenticawithakka.domain.contracts.AgentRole;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.ToolArguments;
import io.agenticawithakka.domain.contracts.ToolRef;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Read tool: searches project knowledge the caller is entitled to (via {@link SearchGateway}). */
public final class SearchKnowledgeTool implements ToolHandler {
    public static final ToolRef REF = new ToolRef("searchKnowledge", 1);
    static final int DEFAULT_RESULTS = 5;

    public static final ToolDefinition DEFINITION = new ToolDefinition(
            REF,
            "Search runbooks and knowledge documents in the current project. Returns cited passages.",
            ToolRisk.READ,
            Set.of(AgentRole.INVESTIGATION),
            List.of(ArgumentSpec.text("query", true, 500), ArgumentSpec.integer("maxResults", false, 1, 10)),
            null,
            Duration.ofSeconds(10),
            1,
            10,
            30_000);

    private final SearchGateway search;

    public SearchKnowledgeTool(SearchGateway search) {
        this.search = ContractValidation.required(search, "search");
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public CompletionStage<List<EvidencePassage>> execute(ToolInvocationContext context, ToolArguments arguments) {
        String maxResults = arguments.values().get("maxResults");
        int limit = maxResults == null ? DEFAULT_RESULTS : Integer.parseInt(maxResults);
        return search.search(new SearchQuery(context.projectId(), context.identityContextRef(),
                arguments.values().get("query"), limit, context.deadline()));
    }
}
