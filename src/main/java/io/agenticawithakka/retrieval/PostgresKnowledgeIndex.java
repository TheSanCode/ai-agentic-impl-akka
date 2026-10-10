package io.agenticawithakka.retrieval;

import io.agenticawithakka.application.ports.AdapterCapabilities;
import io.agenticawithakka.application.ports.EmbeddingVector;
import io.agenticawithakka.application.ports.KnowledgeIndex;
import io.agenticawithakka.domain.contracts.Classification;
import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.EvidenceRef;
import io.agenticawithakka.domain.contracts.KnowledgeChunk;
import io.agenticawithakka.domain.contracts.ProjectId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/** PostgreSQL full-text and pgvector index for synthetic local-lab knowledge. */
public final class PostgresKnowledgeIndex implements KnowledgeIndex {
    private static final AdapterCapabilities CAPABILITIES = new AdapterCapabilities(
            "postgres-pgvector-index",
            Set.of("project-filtered", "source-filtered", "versioned-chunks", "full-text", "vector-cosine"),
            List.of(
                    "Requires PostgreSQL with pgvector and a separately selected EmbeddingGateway.",
                    "Designed for synthetic local-lab data; production retention and provider policy are separate."));
    private static final int RECIPROCAL_RANK_CONSTANT = 60;
    private static final double MINIMUM_COSINE_SIMILARITY = 0.15;

    private static final String UPSERT = """
            INSERT INTO knowledge_chunks (
                project_id, source_id, source_version, chunk_id, classification, observed_at,
                permission_ref, source_link, content, embedding_model_id, embedding_dimensions, embedding
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector)
            ON CONFLICT (project_id, source_id, source_version, chunk_id, embedding_model_id)
            DO UPDATE SET classification = EXCLUDED.classification,
                          observed_at = EXCLUDED.observed_at,
                          permission_ref = EXCLUDED.permission_ref,
                          source_link = EXCLUDED.source_link,
                          content = EXCLUDED.content,
                          embedding_dimensions = EXCLUDED.embedding_dimensions,
                          embedding = EXCLUDED.embedding
            """;

    private static final String SEARCH = """
            WITH keyword_hits AS (
                SELECT project_id, source_id, source_version, chunk_id, embedding_model_id,
                       row_number() OVER (
                           ORDER BY ts_rank_cd(search_vector, plainto_tsquery('simple', ?)) DESC,
                                    observed_at DESC, source_id, source_version, chunk_id
                       ) AS position
                FROM knowledge_chunks
                WHERE project_id = ? AND source_id = ANY (?) AND embedding_model_id = ?
                  AND embedding_dimensions = ?
                  AND search_vector @@ plainto_tsquery('simple', ?)
                ORDER BY position
                LIMIT ?
            ), semantic_hits AS (
                SELECT project_id, source_id, source_version, chunk_id, embedding_model_id,
                       row_number() OVER (
                           ORDER BY embedding <=> ?::vector, observed_at DESC, source_id, source_version, chunk_id
                       ) AS position
                FROM knowledge_chunks
                WHERE project_id = ? AND source_id = ANY (?) AND embedding_model_id = ?
                  AND embedding_dimensions = ?
                  AND 1 - (embedding <=> ?::vector) >= ?
                ORDER BY position
                LIMIT ?
            ), ranked AS (
                SELECT project_id, source_id, source_version, chunk_id, embedding_model_id,
                       1.0 / (? + position) AS score
                FROM keyword_hits
                UNION ALL
                SELECT project_id, source_id, source_version, chunk_id, embedding_model_id,
                       1.0 / (? + position) AS score
                FROM semantic_hits
            ), fused AS (
                SELECT project_id, source_id, source_version, chunk_id, embedding_model_id,
                       sum(score) AS score
                FROM ranked
                GROUP BY project_id, source_id, source_version, chunk_id, embedding_model_id
            )
            SELECT d.source_id, d.source_version, d.project_id, d.classification, d.observed_at,
                   d.permission_ref, d.source_link, d.content
            FROM fused f
            JOIN knowledge_chunks d
              USING (project_id, source_id, source_version, chunk_id, embedding_model_id)
            ORDER BY f.score DESC, d.observed_at DESC, d.chunk_id
            LIMIT ?
            """;

    private static final String DELETE_SOURCE_VERSION = """
            DELETE FROM knowledge_chunks
            WHERE project_id = ? AND source_id = ? AND source_version = ?
            """;

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresKnowledgeIndex(DataSource dataSource, Executor executor) {
        this.dataSource = ContractValidation.required(dataSource, "dataSource");
        this.executor = ContractValidation.required(executor, "executor");
    }

    @Override
    public AdapterCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletionStage<Void> upsert(KnowledgeChunk chunk, EmbeddingVector embedding) {
        ContractValidation.required(chunk, "chunk");
        ContractValidation.required(embedding, "embedding");
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                var ref = chunk.evidenceRef();
                statement.setString(1, ref.projectId().value());
                statement.setString(2, ref.sourceId());
                statement.setString(3, ref.sourceVersion());
                statement.setString(4, chunk.chunkId());
                statement.setString(5, ref.classification().name());
                statement.setTimestamp(6, Timestamp.from(ref.observedAt()));
                statement.setString(7, ref.permissionRef());
                statement.setString(8, ref.sourceLink());
                statement.setString(9, chunk.text());
                statement.setString(10, embedding.modelId());
                statement.setInt(11, embedding.dimensions());
                statement.setString(12, embedding.pgvectorLiteral());
                statement.executeUpdate();
            } catch (SQLException failure) {
                throw new IllegalStateException("knowledge index write failed", failure);
            }
        }, executor);
    }

    @Override
    public CompletionStage<List<EvidencePassage>> search(
            ProjectId projectId,
            Set<String> permittedSourceIds,
            String query,
            EmbeddingVector queryEmbedding,
            int maxResults) {
        ContractValidation.required(projectId, "projectId");
        ContractValidation.set(permittedSourceIds, "permittedSourceIds", 64);
        ContractValidation.text(query, "query", 2_000);
        ContractValidation.required(queryEmbedding, "queryEmbedding");
        ContractValidation.positive(maxResults, "maxResults");
        if (permittedSourceIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        if (maxResults > 50) {
            throw new IllegalArgumentException("maxResults exceeds 50");
        }
        return CompletableFuture.supplyAsync(
                () -> searchNow(projectId, permittedSourceIds, query, queryEmbedding, maxResults), executor);
    }

    @Override
    public CompletionStage<Integer> deleteSourceVersion(ProjectId projectId, String sourceId, String sourceVersion) {
        ContractValidation.required(projectId, "projectId");
        ContractValidation.matches(sourceId, "sourceId", ContractValidation.REFERENCE);
        ContractValidation.matches(sourceVersion, "sourceVersion", ContractValidation.REFERENCE);
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(DELETE_SOURCE_VERSION)) {
                statement.setString(1, projectId.value());
                statement.setString(2, sourceId);
                statement.setString(3, sourceVersion);
                return statement.executeUpdate();
            } catch (SQLException failure) {
                throw new IllegalStateException("knowledge index deletion failed", failure);
            }
        }, executor);
    }

    private List<EvidencePassage> searchNow(
            ProjectId projectId,
            Set<String> permittedSourceIds,
            String query,
            EmbeddingVector queryEmbedding,
            int maxResults) {
        int candidateLimit = Math.min(100, Math.max(10, maxResults * 4));
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SEARCH)) {
            var sources = connection.createArrayOf("text", permittedSourceIds.toArray(String[]::new));
            try {
                statement.setString(1, query);
                statement.setString(2, projectId.value());
                statement.setArray(3, sources);
                statement.setString(4, queryEmbedding.modelId());
                statement.setInt(5, queryEmbedding.dimensions());
                statement.setString(6, query);
                statement.setInt(7, candidateLimit);
                statement.setString(8, queryEmbedding.pgvectorLiteral());
                statement.setString(9, projectId.value());
                statement.setArray(10, sources);
                statement.setString(11, queryEmbedding.modelId());
                statement.setInt(12, queryEmbedding.dimensions());
                statement.setString(13, queryEmbedding.pgvectorLiteral());
                statement.setDouble(14, MINIMUM_COSINE_SIMILARITY);
                statement.setInt(15, candidateLimit);
                statement.setInt(16, RECIPROCAL_RANK_CONSTANT);
                statement.setInt(17, RECIPROCAL_RANK_CONSTANT);
                statement.setInt(18, maxResults);
                try (ResultSet rows = statement.executeQuery()) {
                    List<EvidencePassage> results = new ArrayList<>();
                    while (rows.next()) {
                        var ref = new EvidenceRef(
                                rows.getString("source_id"),
                                rows.getString("source_version"),
                                new ProjectId(rows.getString("project_id")),
                                Classification.valueOf(rows.getString("classification")),
                                rows.getTimestamp("observed_at").toInstant(),
                                rows.getString("permission_ref"),
                                rows.getString("source_link"));
                        results.add(new EvidencePassage(ref, rows.getString("content")));
                    }
                    return List.copyOf(results);
                }
            } finally {
                sources.free();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("knowledge index search failed", failure);
        }
    }
}
