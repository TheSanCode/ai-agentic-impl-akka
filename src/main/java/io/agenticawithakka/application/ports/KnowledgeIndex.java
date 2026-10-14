package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.KnowledgeChunk;
import io.agenticawithakka.domain.contracts.ProjectId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Replaceable persistence boundary for versioned knowledge chunks and hybrid retrieval. */
public interface KnowledgeIndex {
    AdapterCapabilities capabilities();

    CompletionStage<Void> upsert(KnowledgeChunk chunk, EmbeddingVector embedding);

    CompletionStage<List<EvidencePassage>> search(
            ProjectId projectId,
            Set<String> permittedSourceIds,
            String query,
            EmbeddingVector queryEmbedding,
            int maxResults);

    CompletionStage<Integer> deleteSourceVersion(ProjectId projectId, String sourceId, String sourceVersion);
}
