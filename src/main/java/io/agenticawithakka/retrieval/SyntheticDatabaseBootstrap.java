package io.agenticawithakka.retrieval;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs migrations and clears only knowledge rows after ownership verification. */
public final class SyntheticDatabaseBootstrap {
    private final DataSource dataSource;
    private final SyntheticDatabaseOwnershipGuard ownership;

    public SyntheticDatabaseBootstrap(DataSource dataSource, SyntheticDatabaseOwnershipGuard ownership) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource");
        this.ownership = java.util.Objects.requireNonNull(ownership, "ownership");
    }

    public void initialize() {
        ownership.verifyOwnership();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("agentica_synthetic")
                .defaultSchema("agentica_synthetic")
                .createSchemas(false)
                .cleanDisabled(true)
                .load()
                .migrate();
        new JdbcTemplate(dataSource).update("DELETE FROM agentica_synthetic.knowledge_chunks");
    }
}
