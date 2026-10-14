package io.agenticawithakka.api;

import io.agenticawithakka.domain.contracts.Classification;
import io.agenticawithakka.domain.contracts.EvidenceRef;
import io.agenticawithakka.domain.contracts.KnowledgeChunk;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.retrieval.KnowledgeIngestionService;
import io.agenticawithakka.security.AuthenticatedIdentityContextResolver;
import java.time.Clock;
import java.util.concurrent.CompletionException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.ErrorCode;

/** Explicitly ingests one fixed synthetic runbook after current user/source authorization. */
@RestController
@RequestMapping("/api/projects/{projectId}/knowledge/synthetic")
@Profile("synthetic-postgres")
public final class SyntheticKnowledgeController {
    private static final String SOURCE_ID = "mock-knowledge";
    private static final String SOURCE_VERSION = "synthetic-v1";
    private static final String CHUNK_ID = "billing-connection-timeouts";
    private static final String SOURCE_LINK = "https://docs.example.invalid/runbooks/billing-connection-timeouts";
    private static final String CONTENT =
            "Synthetic runbook: inspect billing connection-pool saturation and delayed worker acquisition.";

    private final AuthenticatedIdentityContextResolver identities;
    private final KnowledgeIngestionService ingestion;
    private final Clock clock;

    public SyntheticKnowledgeController(
            AuthenticatedIdentityContextResolver identities, KnowledgeIngestionService ingestion, Clock clock) {
        this.identities = identities;
        this.ingestion = ingestion;
        this.clock = clock;
    }

    @PostMapping("/billing-timeout-runbook")
    public ResponseEntity<IngestionReceipt> ingest(
            @PathVariable String projectId, Authentication authentication) {
        ProjectId project = new ProjectId(projectId);
        var identity = identities.resolve(authentication);
        var reference = new EvidenceRef(
                SOURCE_ID,
                SOURCE_VERSION,
                project,
                Classification.INTERNAL,
                clock.instant(),
                "permission:" + project.value() + "-" + SOURCE_ID,
                SOURCE_LINK);
        var chunk = new KnowledgeChunk(CHUNK_ID, reference, CONTENT);
        try {
            ingestion.ingest(identity, chunk).toCompletableFuture().join();
        } catch (CompletionException failure) {
            Throwable cause = failure;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new PortException(ErrorCode.DEPENDENCY_UNAVAILABLE, "synthetic knowledge ingestion failed");
        }
        return ResponseEntity.ok(new IngestionReceipt(SOURCE_ID, SOURCE_VERSION, CHUNK_ID));
    }

    public record IngestionReceipt(String sourceId, String sourceVersion, String chunkId) {}
}
