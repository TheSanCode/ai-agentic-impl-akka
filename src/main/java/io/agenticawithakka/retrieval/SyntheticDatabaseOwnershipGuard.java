package io.agenticawithakka.retrieval;

import io.agenticawithakka.retrieval.SyntheticRetrievalProperties;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/** Verifies an explicitly provisioned loopback synthetic database before migrations or reset. */
public final class SyntheticDatabaseOwnershipGuard {
    private static final Pattern JDBC_URL = Pattern.compile(
            "jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/([a-z][a-z0-9_]*)");
    private static final String OWNERSHIP_QUERY = """
            SELECT application_id, owner_id::text, data_classification, database_name
            FROM agentica_synthetic.database_ownership
            """;

    private final DataSource dataSource;
    private final String jdbcUrl;
    private final SyntheticRetrievalProperties retrieval;

    public SyntheticDatabaseOwnershipGuard(
            DataSource dataSource, String jdbcUrl, SyntheticRetrievalProperties retrieval) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource");
        this.jdbcUrl = java.util.Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.retrieval = java.util.Objects.requireNonNull(retrieval, "retrieval");
    }

    public void verifyOwnership() {
        Matcher jdbc = JDBC_URL.matcher(jdbcUrl);
        if (!jdbc.matches()
                || Integer.parseInt(jdbc.group(1)) < 1
                || Integer.parseInt(jdbc.group(1)) > 65_535
                || !retrieval.databaseName().equals(jdbc.group(2))
                || !"agentica_synthetic".equals(retrieval.schemaName())) {
            throw new IllegalStateException(
                    "Synthetic database target is not the configured loopback application database");
        }

        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(OWNERSHIP_QUERY);
                var rows = statement.executeQuery()) {
            if (!retrieval.databaseName().equals(connection.getCatalog())
                    || !rows.next()
                    || !"agenticawithakka".equals(rows.getString("application_id"))
                    || !retrieval.ownerId().toString().equals(rows.getString("owner_id"))
                    || !"synthetic-only".equals(rows.getString("data_classification"))
                    || !retrieval.databaseName().equals(rows.getString("database_name"))
                    || rows.next()) {
                throw new IllegalStateException(
                        "Synthetic database ownership marker does not match; refusing migration or reset");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(
                    "Synthetic database ownership marker could not be verified; refusing migration or reset",
                    failure);
        }
    }
}
