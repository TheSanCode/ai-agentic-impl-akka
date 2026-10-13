package io.agenticawithakka.config;

import io.agenticawithakka.application.ports.EmbeddingGateway;
import io.agenticawithakka.application.ports.KnowledgeIndex;
import io.agenticawithakka.application.ports.PolicyDecisionService;
import io.agenticawithakka.application.ports.SearchGateway;
import io.agenticawithakka.retrieval.KnowledgeIngestionService;
import io.agenticawithakka.retrieval.OllamaEmbeddingGateway;
import io.agenticawithakka.retrieval.PostgresKnowledgeIndex;
import io.agenticawithakka.retrieval.PostgresSearchGateway;
import io.agenticawithakka.retrieval.SyntheticDatabaseBootstrap;
import io.agenticawithakka.retrieval.SyntheticDatabaseOwnershipGuard;
import io.agenticawithakka.retrieval.SyntheticRetrievalProperties;
import io.agenticawithakka.security.MockIdentityContextStore;
import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@Profile("synthetic-postgres")
@org.springframework.boot.context.properties.EnableConfigurationProperties(SyntheticRetrievalProperties.class)
public class SyntheticPostgresConfiguration {
    @Bean(destroyMethod = "close")
    HikariDataSource syntheticDataSource(Environment environment) {
        var hikari = new HikariConfig();
        hikari.setJdbcUrl(environment.getRequiredProperty("spring.datasource.url"));
        hikari.setUsername(environment.getRequiredProperty("spring.datasource.username"));
        hikari.setPassword(environment.getRequiredProperty("spring.datasource.password"));
        hikari.setSchema("agentica_synthetic");
        hikari.setMaximumPoolSize(4);
        return new HikariDataSource(hikari);
    }

    @Bean(destroyMethod = "shutdown")
    ThreadPoolExecutor retrievalExecutor() {
        return new ThreadPoolExecutor(
                2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    EmbeddingGateway ollamaEmbeddingGateway(
            SyntheticRetrievalProperties properties, JsonMapper json, ThreadPoolExecutor retrievalExecutor) {
        return new OllamaEmbeddingGateway(properties, json, retrievalExecutor);
    }

    @Bean
    KnowledgeIndex postgresKnowledgeIndex(
            DataSource dataSource, ThreadPoolExecutor retrievalExecutor, SyntheticDatabaseBootstrap bootstrap) {
        return new PostgresKnowledgeIndex(dataSource, retrievalExecutor);
    }

    @Bean
    SearchGateway postgresSearchGateway(
            KnowledgeIndex index,
            EmbeddingGateway embeddings,
            MockIdentityContextStore identities,
            PolicyDecisionService policy,
            Clock clock) {
        return new PostgresSearchGateway(index, embeddings, identities, policy, clock);
    }

    @Bean
    KnowledgeIngestionService knowledgeIngestionService(
            MockIdentityContextStore identities,
            PolicyDecisionService policy,
            EmbeddingGateway embeddings,
            KnowledgeIndex index) {
        return new KnowledgeIngestionService(identities, policy, embeddings, index);
    }

    @Bean
    SyntheticDatabaseOwnershipGuard syntheticDatabaseOwnershipGuard(
            DataSource dataSource, Environment environment, SyntheticRetrievalProperties properties) {
        return new SyntheticDatabaseOwnershipGuard(
                dataSource, environment.getRequiredProperty("spring.datasource.url"), properties);
    }

    @Bean(initMethod = "initialize")
    SyntheticDatabaseBootstrap syntheticDatabaseBootstrap(
            DataSource dataSource, SyntheticDatabaseOwnershipGuard ownership) {
        return new SyntheticDatabaseBootstrap(dataSource, ownership);
    }
}
