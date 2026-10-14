package io.agenticawithakka.retrieval;

import io.agenticawithakka.application.ports.EmbeddingGateway;
import io.agenticawithakka.application.ports.KnowledgeIndex;
import io.agenticawithakka.application.ports.PolicyAction;
import io.agenticawithakka.application.ports.PolicyDecision;
import io.agenticawithakka.application.ports.PolicyDecisionService;
import io.agenticawithakka.application.ports.PolicyRequest;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.KnowledgeChunk;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.security.MockIdentityContextStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Authorized, idempotent single-chunk ingestion for the local synthetic knowledge slice. */
public final class KnowledgeIngestionService {
    private final MockIdentityContextStore identities;
    private final PolicyDecisionService policy;
    private final EmbeddingGateway embeddings;
    private final KnowledgeIndex index;

    public KnowledgeIngestionService(
            MockIdentityContextStore identities,
            PolicyDecisionService policy,
            EmbeddingGateway embeddings,
            KnowledgeIndex index) {
        this.identities = ContractValidation.required(identities, "identities");
        this.policy = ContractValidation.required(policy, "policy");
        this.embeddings = ContractValidation.required(embeddings, "embeddings");
        this.index = ContractValidation.required(index, "index");
    }

    public CompletionStage<Void> ingest(IdentityContextRef identityRef, KnowledgeChunk chunk) {
        ContractValidation.required(identityRef, "identityRef");
        ContractValidation.required(chunk, "chunk");
        var ref = chunk.evidenceRef();
        return authorize(identityRef, ref.projectId(), ref.sourceId())
                .thenCompose(ignored -> {
                    var embeddingStage = embeddings.embed(chunk.text());
                    if (embeddingStage == null) {
                        return CompletableFuture.failedFuture(new PortException(
                                ErrorCode.DEPENDENCY_UNAVAILABLE, "embedding provider returned no result"));
                    }
                    return embeddingStage.thenCompose(embedding ->
                            authorize(identityRef, ref.projectId(), ref.sourceId())
                                    .thenCompose(allowed -> index.upsert(chunk, embedding)));
                });
    }

    public CompletionStage<Integer> deleteSourceVersion(
            IdentityContextRef identityRef, ProjectId projectId, String sourceId, String sourceVersion) {
        ContractValidation.required(identityRef, "identityRef");
        ContractValidation.required(projectId, "projectId");
        ContractValidation.matches(sourceId, "sourceId", ContractValidation.REFERENCE);
        ContractValidation.matches(sourceVersion, "sourceVersion", ContractValidation.REFERENCE);
        return authorize(identityRef, projectId, sourceId)
                .thenCompose(ignored -> authorize(identityRef, projectId, sourceId))
                .thenCompose(ignored -> index.deleteSourceVersion(projectId, sourceId, sourceVersion));
    }

    private CompletionStage<Void> authorize(
            IdentityContextRef identityRef, ProjectId projectId, String sourceId) {
        var identity = identities.resolve(identityRef)
                .orElseThrow(() -> new PortException(ErrorCode.AUTHENTICATION_REQUIRED, "identity context is unavailable"));
        if (!identity.belongsTo(projectId) || !identity.mayAccessSource(projectId, sourceId)) {
            return CompletableFuture.failedFuture(new PortException(ErrorCode.DENIED, "source access is not authorized"));
        }
        var decisionStage = policy.decide(new PolicyRequest(identityRef, projectId, PolicyAction.READ_EVIDENCE, sourceId));
        if (decisionStage == null) {
            return CompletableFuture.failedFuture(
                    new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "authorization returned no decision"));
        }
        return decisionStage.thenApply(decision -> {
            if (decision == null) {
                throw new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "authorization returned no decision");
            }
            if (!decision.permitted()) {
                throw new PortException(ErrorCode.DENIED, "source access is not authorized");
            }
            return null;
        });
    }
}
