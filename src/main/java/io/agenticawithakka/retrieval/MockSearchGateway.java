package io.agenticawithakka.retrieval;

import io.agenticawithakka.application.ports.AdapterCapabilities;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.application.ports.SearchGateway;
import io.agenticawithakka.application.ports.SearchQuery;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.security.MockIdentityContextStore;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Synthetic in-memory knowledge search that filters by the current project and source grant. */
public final class MockSearchGateway implements SearchGateway {
    private static final AdapterCapabilities CAPABILITIES = new AdapterCapabilities(
            "mock-search",
            Set.of("project-scoped", "current-source-grant", "synthetic-data"),
            List.of("Process-local synthetic knowledge only; no ingestion or durable index."));

    private final MockIdentityContextStore identities;
    private final Clock clock;
    private final List<EvidencePassage> passages;

    public MockSearchGateway(
            MockIdentityContextStore identities, Clock clock, List<EvidencePassage> passages) {
        this.identities = ContractValidation.required(identities, "identities");
        this.clock = ContractValidation.required(clock, "clock");
        this.passages = ContractValidation.list(passages, "passages", 500);
    }

    @Override
    public AdapterCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletionStage<List<EvidencePassage>> search(SearchQuery query) {
        ContractValidation.required(query, "query");
        try {
            if (!clock.instant().isBefore(query.deadline())) {
                throw new PortException(ErrorCode.DEADLINE_EXCEEDED, "search deadline has expired");
            }
            var identity = identities.resolve(query.identityContextRef())
                    .orElseThrow(() -> new PortException(
                            ErrorCode.AUTHENTICATION_REQUIRED, "authenticated identity is unavailable"));
            if (!identity.belongsTo(query.projectId())) {
                throw new PortException(ErrorCode.DENIED, "identity is not a member of the requested project");
            }
            var results = passages.stream()
                    .filter(passage -> passage.ref().projectId().equals(query.projectId()))
                    .filter(passage -> identity.mayAccessSource(query.projectId(), passage.ref().sourceId()))
                    .limit(query.maxResults())
                    .toList();
            return CompletableFuture.completedFuture(results);
        } catch (PortException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
