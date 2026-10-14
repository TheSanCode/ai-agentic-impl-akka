package io.agenticawithakka.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agenticawithakka.AgenticaApplication;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.application.ports.SearchGateway;
import io.agenticawithakka.application.ports.SearchQuery;
import io.agenticawithakka.domain.contracts.Classification;
import io.agenticawithakka.domain.contracts.EvidenceRef;
import io.agenticawithakka.domain.contracts.KnowledgeChunk;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.security.MockIdentityContextStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class SyntheticPostgresProfileIntegrationTest {
    private static final String DB_NAME = "agenticawithakka_synthetic";
    private static final String SCHEMA = "agentica_synthetic";
    private static final UUID OWNER_ID = UUID.fromString("151d4fa3-43a7-4821-a4ae-16513812569e");
    private static final ProjectId ALPHA = new ProjectId("alpha");
    private static final ProjectId BETA = new ProjectId("beta");
    private static final String ISSUER = "https://issuer.example";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "pgvector/pgvector:0.8.7-pg17@sha256:ac08538c6f8b9904c33c8224c5e5706dbe760aca29db1d096972b4052c22a75d")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName(DB_NAME)
            .withUsername("synthetic_test")
            .withPassword("synthetic_test_only")
            .withInitScript("db/synthetic-ownership-init.sql");

    @Test
    void usesRealOllamaEmbeddingsForAuthorizedHybridSearchAndResetsOnlyOwnedDatabaseOnRestart() {
        try (var context = startApplication(OWNER_ID)) {
            var identities = context.getBean(MockIdentityContextStore.class);
            var ingestion = context.getBean(KnowledgeIngestionService.class);
            var search = context.getBean(SearchGateway.class);
            var alphaIdentity = identities.registerAuthenticatedSubject(
                    ISSUER + "\nalice", Map.of(ALPHA, access("mock-knowledge")));
            var betaIdentity = identities.registerAuthenticatedSubject(
                    ISSUER + "\nbob", Map.of(BETA, access("mock-knowledge")));

            ingestion.ingest(alphaIdentity, chunk(ALPHA, "alpha-pool-v1"))
                    .toCompletableFuture()
                    .join();
            ingestion.ingest(betaIdentity, chunk(BETA, "beta-pool-v1"))
                    .toCompletableFuture()
                    .join();
            assertThat(rowCount(context)).isEqualTo(2);

            var alphaResults = search.search(query(ALPHA, alphaIdentity)).toCompletableFuture().join();
            var betaResults = search.search(query(BETA, betaIdentity)).toCompletableFuture().join();
            assertThat(alphaResults).singleElement().satisfies(result -> {
                assertThat(result.text()).contains("Synthetic runbook", "connection-pool saturation");
                assertThat(result.ref().projectId()).isEqualTo(ALPHA);
                assertThat(result.ref().sourceId()).isEqualTo("mock-knowledge");
                assertThat(result.ref().sourceVersion()).isEqualTo("synthetic-v1");
                assertThat(result.ref().permissionRef()).isEqualTo("permission:alpha-mock-knowledge");
                assertThat(result.ref().sourceLink()).isEqualTo(
                        "https://docs.example.invalid/runbooks/billing-connection-timeouts");
            });
            assertThat(betaResults).singleElement()
                    .extracting(result -> result.ref().projectId())
                    .isEqualTo(BETA);
            assertThatThrownBy(() -> ingestion.ingest(betaIdentity, chunk(ALPHA, "alpha-forged"))
                            .toCompletableFuture()
                            .join())
                    .hasRootCauseInstanceOf(PortException.class);

            identities.revokeProjectMembership(alphaIdentity, ALPHA);
            assertThatThrownBy(() -> search.search(query(ALPHA, alphaIdentity)).toCompletableFuture().join())
                    .hasRootCauseInstanceOf(PortException.class);
            assertThat(rowCount(context)).isEqualTo(2);
        }

        assertThatThrownBy(() -> startApplication(UUID.randomUUID()))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(rowCount()).isEqualTo(2);

        try (var restarted = startApplication(OWNER_ID)) {
            assertThat(rowCount(restarted)).isZero();
        }
    }

    private static ConfigurableApplicationContext startApplication(UUID ownerId) {
        Map<String, Object> overrides = Map.of(
                "AGENTICA_SYNTHETIC_JDBC_URL", POSTGRES.getJdbcUrl(),
                "AGENTICA_SYNTHETIC_DB_USERNAME", POSTGRES.getUsername(),
                "AGENTICA_SYNTHETIC_DB_PASSWORD", POSTGRES.getPassword(),
                "AGENTICA_SYNTHETIC_DB_OWNER_ID", ownerId.toString(),
                "server.port", "0");
        return new SpringApplicationBuilder(AgenticaApplication.class)
                .profiles("synthetic-postgres")
                .web(WebApplicationType.SERVLET)
                .initializers(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("synthetic-test", overrides)))
                .run();
    }

    private static int rowCount(ConfigurableApplicationContext context) {
        return context.getBean(JdbcTemplate.class).queryForObject(
                "SELECT count(*) FROM " + SCHEMA + ".knowledge_chunks", Integer.class);
    }

    private static int rowCount() {
        var source = new org.postgresql.ds.PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return new JdbcTemplate(source).queryForObject(
                "SELECT count(*) FROM " + SCHEMA + ".knowledge_chunks", Integer.class);
    }

    private static SearchQuery query(ProjectId project, io.agenticawithakka.domain.contracts.IdentityContextRef identity) {
        return new SearchQuery(
                project,
                identity,
                "billing connection pool delayed acquisition",
                5,
                Instant.now().plus(Duration.ofMinutes(2)));
    }

    private static KnowledgeChunk chunk(ProjectId project, String chunkId) {
        return new KnowledgeChunk(
                chunkId,
                new EvidenceRef(
                        "mock-knowledge",
                        "synthetic-v1",
                        project,
                        Classification.INTERNAL,
                        Instant.parse("2026-10-12T00:00:00Z"),
                        "permission:" + project.value() + "-mock-knowledge",
                        "https://docs.example.invalid/runbooks/billing-connection-timeouts"),
                "Synthetic runbook: inspect billing connection-pool saturation and delayed worker acquisition.");
    }

    private static MockIdentityContextStore.ProjectAccess access(String source) {
        return new MockIdentityContextStore.ProjectAccess(Set.of("service:billing"), Set.of(source));
    }
}
