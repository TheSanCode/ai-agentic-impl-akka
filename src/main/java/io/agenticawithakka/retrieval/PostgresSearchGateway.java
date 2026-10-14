package io.agenticawithakka.retrieval;

import io.agenticawithakka.application.ports.AdapterCapabilities;
import io.agenticawithakka.application.ports.EmbeddingGateway;
import io.agenticawithakka.application.ports.KnowledgeIndex;
import io.agenticawithakka.application.ports.PolicyAction;
import io.agenticawithakka.application.ports.PolicyDecisionService;
import io.agenticawithakka.application.ports.PolicyRequest;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.application.ports.SearchGateway;
import io.agenticawithakka.application.ports.SearchQuery;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.security.MockIdentityContextStore;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Permission-filtered reciprocal-rank hybrid search over PostgreSQL full-text and pgvector. */
public final class PostgresSearchGateway implements SearchGateway {
    private static final AdapterCapabilities CAPABILITIES = new AdapterCapabilities(
            "postgres-pgvector-search",
            Set.of("keyword-search", "cosine-vector-search", "reciprocal-rank-fusion", "current-acl-filtering"),
            List.of(
                    "Requires an explicitly selected EmbeddingGateway with matching indexed model/version.",
                    "Local synthetic data only; this adapter is not configured as the default application search."));

    private final KnowledgeIndex index;
    private final EmbeddingGateway embeddings;
    private final MockIdentityContextStore identities;
    private final PolicyDecisionService policy;
    private final Clock clock;

    public PostgresSearchGateway(
            KnowledgeIndex index,
            EmbeddingGateway embeddings,
            MockIdentityContextStore identities,
            PolicyDecisionService policy,
            Clock clock) {
        this.index = java.util.Objects.requireNonNull(index, "index");
        this.embeddings = java.util.Objects.requireNonNull(embeddings, "embeddings");
        this.identities = java.util.Objects.requireNonNull(identities, "identities");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AdapterCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletionStage<List<EvidencePassage>> search(SearchQuery query) {
        java.util.Objects.requireNonNull(query, "query");
        try {
            ensureBeforeDeadline(query);
            currentIdentity(query);
            var embeddingStage = embeddings.embed(query.text());
            if (embeddingStage == null) {
                return CompletableFuture.failedFuture(
                        new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "embedding provider returned no result"));
            }
            return embeddingStage.thenCompose(embedding -> {
                ensureBeforeDeadline(query);
                var current = currentIdentity(query);
                Set<String> sources = current.projectAccess().get(query.projectId()).sources();
                if (sources.isEmpty()) {
                    return CompletableFuture.completedFuture(List.of());
                }
                return index.search(query.projectId(), sources, query.text(), embedding, query.maxResults())
                        .thenCompose(passages -> authorizeResults(query, passages));
            });
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletionStage<List<EvidencePassage>> authorizeResults(
            SearchQuery query, List<EvidencePassage> passages) {
        ensureBeforeDeadline(query);
        var current = currentIdentity(query);
        Set<String> currentSources = current.projectAccess().get(query.projectId()).sources();
        var visibleCandidates = passages.stream()
                .filter(passage -> passage.ref().projectId().equals(query.projectId()))
                .filter(passage -> currentSources.contains(passage.ref().sourceId()))
                .toList();
        var checks = visibleCandidates.stream()
                .map(EvidencePassage::ref)
                .map(ref -> policy.decide(new PolicyRequest(
                        query.identityContextRef(), query.projectId(), PolicyAction.READ_EVIDENCE, ref.sourceId())))
                .toList();
        if (checks.stream().anyMatch(java.util.Objects::isNull)) {
            return CompletableFuture.failedFuture(
                    new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "authorization returned no decision"));
        }
        var decisions = checks.stream()
                .map(check -> check.thenApply(decision -> {
                    if (decision == null) {
                        throw new PortException(
                                ErrorCode.DEPENDENCY_UNAVAILABLE, "authorization returned no decision");
                    }
                    return decision;
                }))
                .map(CompletionStage::toCompletableFuture)
                .toList();
        return CompletableFuture.allOf(decisions.toArray(CompletableFuture<?>[]::new)).thenApply(ignored -> {
            ensureBeforeDeadline(query);
            var latest = currentIdentity(query);
            Set<String> latestSources = latest.projectAccess().get(query.projectId()).sources();
            return java.util.stream.IntStream.range(0, visibleCandidates.size())
                    .filter(index -> latestSources.contains(visibleCandidates.get(index).ref().sourceId()))
                    .filter(index -> decisions.get(index).join().permitted())
                    .mapToObj(visibleCandidates::get)
                    .limit(query.maxResults())
                    .toList();
        });
    }

    private MockIdentityContextStore.IdentityContext currentIdentity(SearchQuery query) {
        var identity = identities.resolve(query.identityContextRef())
                .orElseThrow(() -> new PortException(
                        ErrorCode.AUTHENTICATION_REQUIRED, "authenticated identity is unavailable"));
        if (!identity.belongsTo(query.projectId())) {
            throw new PortException(ErrorCode.DENIED, "identity is not a member of the requested project");
        }
        return identity;
    }

    private void ensureBeforeDeadline(SearchQuery query) {
        if (!clock.instant().isBefore(query.deadline())) {
            throw new PortException(ErrorCode.DEADLINE_EXCEEDED, "search deadline has expired");
        }
    }
}
