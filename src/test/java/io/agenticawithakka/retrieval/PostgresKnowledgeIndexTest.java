package io.agenticawithakka.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agenticawithakka.application.ports.AdapterCapabilities;
import io.agenticawithakka.application.ports.EmbeddingGateway;
import io.agenticawithakka.application.ports.EmbeddingVector;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.application.ports.SearchQuery;
import io.agenticawithakka.domain.contracts.Classification;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.EvidenceRef;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.KnowledgeChunk;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.security.MockIdentityContextStore;
import io.agenticawithakka.security.MockProjectPolicyService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PostgresKnowledgeIndexTest {
    private static final ProjectId ALPHA = new ProjectId("alpha");
    private static final ProjectId BETA = new ProjectId("beta");
    private static final String ISSUER = "https://issuer.example";
    private static final Instant OBSERVED_AT = Instant.parse("2026-10-10T12:00:00Z");
    private static final EmbeddingVector MATCHING_VECTOR = new EmbeddingVector("fixture-v1", List.of(1.0f, 0.0f));
    private static final EmbeddingVector OTHER_VECTOR = new EmbeddingVector("fixture-v1", List.of(0.0f, 1.0f));
    private static final Set<String> KNOWLEDGE_SOURCE = Set.of("mock-knowledge");
    private static final String POSTGRES_IMAGE =
            "pgvector/pgvector:0.8.7-pg17@sha256:ac08538c6f8b9904c33c8224c5e5706dbe760aca29db1d096972b4052c22a75d";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("agentic")
            .withUsername("synthetic_test")
            .withPassword("synthetic_test_only");

    private static final Clock CLOCK = Clock.systemUTC();
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static DataSource dataSource;
    private PostgresKnowledgeIndex index;

    @BeforeAll
    static void migrateSchema() {
        var postgres = new PGSimpleDataSource();
        postgres.setURL(POSTGRES.getJdbcUrl());
        postgres.setUser(POSTGRES.getUsername());
        postgres.setPassword(POSTGRES.getPassword());
        dataSource = postgres;
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopExecutor() {
        executor.shutdownNow();
    }

    @BeforeEach
    void clearSyntheticRows() throws Exception {
        index = new PostgresKnowledgeIndex(dataSource, executor);
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM knowledge_chunks");
        }
    }

    @Test
    void indexesVersionedChunksAndCombinesKeywordAndVectorRanks() {
        var alphaRunbook = chunk(
                "pool-v1-0",
                "mock-knowledge",
                "runbook-v1",
                ALPHA,
                "Connection pool saturation causes acquisition timeouts.",
                "https://docs.example.invalid/alpha/runbook-v1");
        var alphaSemantic = chunk(
                "pressure-v1-0",
                "mock-knowledge",
                "runbook-v1",
                ALPHA,
                "Capacity pressure remains elevated during billing calls.",
                "https://docs.example.invalid/alpha/pressure-v1");
        var betaRunbook = chunk(
                "pool-v1-0",
                "mock-knowledge",
                "runbook-v1",
                BETA,
                "Connection pool saturation causes acquisition timeouts.",
                "https://docs.example.invalid/beta/runbook-v1");
        index.upsert(alphaRunbook, MATCHING_VECTOR).toCompletableFuture().join();
        index.upsert(alphaSemantic, new EmbeddingVector("fixture-v1", List.of(0.98f, 0.2f)))
                .toCompletableFuture().join();
        index.upsert(betaRunbook, MATCHING_VECTOR).toCompletableFuture().join();

        var matches = index.search(ALPHA, KNOWLEDGE_SOURCE, "connection pool timeout", MATCHING_VECTOR, 5)
                .toCompletableFuture().join();

        assertThat(matches)
                .extracting(passage -> passage.ref().projectId())
                .containsOnly(ALPHA);
        assertThat(matches).extracting(passage -> passage.ref().sourceVersion()).containsOnly("runbook-v1");
        assertThat(matches.getFirst().text()).contains("Connection pool saturation");
        assertThat(matches.getFirst().ref().sourceLink())
                .isEqualTo("https://docs.example.invalid/alpha/runbook-v1");
        assertThat(matches).extracting(EvidencePassage::text).contains(
                "Capacity pressure remains elevated during billing calls.");
    }

    @Test
    void sourceAndModelFiltersExcludeUnauthorizedOrIncompatibleChunks() {
        index.upsert(
                        chunk("allowed", "mock-knowledge", "v1", ALPHA, "pool timeout", null),
                        MATCHING_VECTOR)
                .toCompletableFuture().join();
        index.upsert(
                        chunk("restricted", "private-knowledge", "v1", ALPHA, "pool timeout", null),
                        MATCHING_VECTOR)
                .toCompletableFuture().join();
        index.upsert(
                        chunk("other-model", "mock-knowledge", "v1", ALPHA, "pool timeout", null),
                        new EmbeddingVector("other-model-v1", List.of(1.0f, 0.0f)))
                .toCompletableFuture().join();

        var matches = index.search(ALPHA, KNOWLEDGE_SOURCE, "pool timeout", MATCHING_VECTOR, 5)
                .toCompletableFuture().join();

        assertThat(matches).singleElement().extracting(passage -> passage.text()).isEqualTo("pool timeout");
        assertThat(matches).noneMatch(passage -> passage.ref().sourceId().equals("private-knowledge"));
    }

    @Test
    void upsertReplacesTheSameChunkAndDeleteRemovesOnlyRequestedSourceVersion() {
        var original = chunk("chunk-1", "mock-knowledge", "v1", ALPHA, "old content", null);
        var updated = chunk("chunk-1", "mock-knowledge", "v1", ALPHA, "new connection timeout content", null);
        var nextVersion = chunk("chunk-1", "mock-knowledge", "v2", ALPHA, "newer content", null);

        index.upsert(original, MATCHING_VECTOR).toCompletableFuture().join();
        index.upsert(updated, MATCHING_VECTOR).toCompletableFuture().join();
        index.upsert(nextVersion, MATCHING_VECTOR).toCompletableFuture().join();

        var beforeDelete = index.search(ALPHA, KNOWLEDGE_SOURCE, "connection timeout", MATCHING_VECTOR, 5)
                .toCompletableFuture().join();
        assertThat(beforeDelete).extracting(passage -> passage.ref().sourceVersion()).contains("v1", "v2");

        int deleted = index.deleteSourceVersion(ALPHA, "mock-knowledge", "v1").toCompletableFuture().join();

        assertThat(deleted).isEqualTo(1);
        assertThat(index.search(ALPHA, KNOWLEDGE_SOURCE, "newer content", MATCHING_VECTOR, 5)
                        .toCompletableFuture().join())
                .singleElement()
                .extracting(passage -> passage.ref().sourceVersion())
                .isEqualTo("v2");
    }

    @Test
    void ingestionAndSearchRecheckProjectAndSourceAccess() {
        var identities = new MockIdentityContextStore(CLOCK, Duration.ofMinutes(10));
        var alpha = identities.registerAuthenticatedSubject(
                ISSUER + "\nalice",
                Map.of(ALPHA, access(KNOWLEDGE_SOURCE), BETA, access(KNOWLEDGE_SOURCE)));
        var bob = identities.registerAuthenticatedSubject(
                ISSUER + "\nbob", Map.of(BETA, access(KNOWLEDGE_SOURCE)));
        var policy = new MockProjectPolicyService(identities);
        var embeddings = embeddingGateway(MATCHING_VECTOR);
        var ingestion = new KnowledgeIngestionService(identities, policy, embeddings, index);
        var alphaChunk = chunk(
                "alpha-pool",
                "mock-knowledge",
                "alpha-v1",
                ALPHA,
                "Alpha connection pool timeout guidance.",
                "https://docs.example.invalid/alpha");
        var betaChunk = chunk(
                "beta-pool",
                "mock-knowledge",
                "beta-v1",
                BETA,
                "Beta connection pool timeout guidance.",
                "https://docs.example.invalid/beta");
        ingestion.ingest(alpha, alphaChunk).toCompletableFuture().join();
        ingestion.ingest(bob, betaChunk).toCompletableFuture().join();

        var gateway = new PostgresSearchGateway(index, embeddings, identities, policy, CLOCK);
        var alphaResults = gateway.search(query(ALPHA, alpha)).toCompletableFuture().join();
        var betaResults = gateway.search(query(BETA, bob)).toCompletableFuture().join();

        assertThat(alphaResults).singleElement().extracting(EvidencePassage::text)
                .isEqualTo("Alpha connection pool timeout guidance.");
        assertThat(betaResults).singleElement().extracting(EvidencePassage::text)
                .isEqualTo("Beta connection pool timeout guidance.");
        assertThat(alphaResults.getFirst().ref().sourceLink()).isEqualTo("https://docs.example.invalid/alpha");

        assertThatThrownBy(() -> ingestion.ingest(bob, alphaChunk).toCompletableFuture().join())
                .hasRootCauseInstanceOf(PortException.class)
                .rootCause()
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DENIED);
    }

    @Test
    void searchFailsClosedWhenMembershipIsRevokedDuringEmbedding() {
        var identities = new MockIdentityContextStore(CLOCK, Duration.ofMinutes(10));
        var ref = identities.registerAuthenticatedSubject(ISSUER + "\nalice", Map.of(ALPHA, access(KNOWLEDGE_SOURCE)));
        var policy = new MockProjectPolicyService(identities);
        EmbeddingGateway revokingEmbeddings = new EmbeddingGateway() {
            @Override
            public AdapterCapabilities capabilities() {
                return embeddingGateway(MATCHING_VECTOR).capabilities();
            }

            @Override
            public CompletionStage<EmbeddingVector> embed(String text) {
                identities.revokeProjectMembership(ref, ALPHA);
                return java.util.concurrent.CompletableFuture.completedFuture(MATCHING_VECTOR);
            }
        };
        var gateway = new PostgresSearchGateway(index, revokingEmbeddings, identities, policy, CLOCK);

        assertThatThrownBy(() -> gateway.search(query(ALPHA, ref)).toCompletableFuture().join())
                .hasRootCauseInstanceOf(PortException.class)
                .rootCause()
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DENIED);
    }

    private static SearchQuery query(ProjectId project, IdentityContextRef ref) {
        return new SearchQuery(project, ref, "connection pool timeout", 5, Instant.now().plusSeconds(20));
    }

    private static MockIdentityContextStore.ProjectAccess access(Set<String> sources) {
        return new MockIdentityContextStore.ProjectAccess(Set.of("service:billing"), sources);
    }

    private static KnowledgeChunk chunk(
            String id, String source, String version, ProjectId project, String text, String sourceLink) {
        return new KnowledgeChunk(
                id,
                new EvidenceRef(
                        source,
                        version,
                        project,
                        Classification.INTERNAL,
                        OBSERVED_AT,
                        "permission:" + project.value() + "-" + source,
                        sourceLink),
                text);
    }

    private static EmbeddingGateway embeddingGateway(EmbeddingVector vector) {
        return new EmbeddingGateway() {
            @Override
            public AdapterCapabilities capabilities() {
                return new AdapterCapabilities("fixture-embedding", Set.of("synthetic"), List.of());
            }

            @Override
            public CompletionStage<EmbeddingVector> embed(String text) {
                return java.util.concurrent.CompletableFuture.completedFuture(vector);
            }
        };
    }
}
