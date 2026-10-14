package io.agenticawithakka.retrieval;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import io.agenticawithakka.retrieval.SyntheticRetrievalProperties;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SyntheticDatabaseOwnershipGuardTest {
    private static final UUID OWNER_ID = UUID.fromString("151d4fa3-43a7-4821-a4ae-16513812569e");

    @Test
    void refusesRemoteOrWrongDatabaseTargetsBeforeOpeningAConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        var guard = new SyntheticDatabaseOwnershipGuard(
                dataSource,
                "jdbc:postgresql://database.example.com:5432/agenticawithakka_synthetic",
                properties(OWNER_ID));

        assertThatThrownBy(guard::verifyOwnership)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");

        verify(dataSource, never()).getConnection();
    }

    @Test
    void rejectsAnOwnershipMarkerFromAnotherApplicationOwner() throws Exception {
        var guard = guardWithMarker(rows -> {
            when(rows.getString("application_id")).thenReturn("agenticawithakka");
            when(rows.getString("owner_id")).thenReturn(UUID.randomUUID().toString());
            when(rows.getString("data_classification")).thenReturn("synthetic-only");
            when(rows.getString("database_name")).thenReturn("agenticawithakka_synthetic");
        });

        assertThatThrownBy(guard::verifyOwnership)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ownership marker does not match");
    }

    @Test
    void acceptsOnlyTheExpectedSyntheticDatabaseMarker() throws Exception {
        var guard = guardWithMarker(rows -> {
            when(rows.getString("application_id")).thenReturn("agenticawithakka");
            when(rows.getString("owner_id")).thenReturn(OWNER_ID.toString());
            when(rows.getString("data_classification")).thenReturn("synthetic-only");
            when(rows.getString("database_name")).thenReturn("agenticawithakka_synthetic");
        });

        guard.verifyOwnership();
    }

    private static SyntheticDatabaseOwnershipGuard guardWithMarker(MarkerConfigurer configurer) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(connection.getCatalog()).thenReturn("agenticawithakka_synthetic");
        when(rows.next()).thenReturn(true, false);
        configurer.configure(rows);
        return new SyntheticDatabaseOwnershipGuard(
                dataSource,
                "jdbc:postgresql://127.0.0.1:5432/agenticawithakka_synthetic",
                properties(OWNER_ID));
    }

    private static SyntheticRetrievalProperties properties(UUID ownerId) {
        return new SyntheticRetrievalProperties(
                "agenticawithakka_synthetic",
                "agentica_synthetic",
                ownerId,
                "http://127.0.0.1:11434",
                "nomic-embed-text:latest",
                "0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f",
                768);
    }

    @FunctionalInterface
    private interface MarkerConfigurer {
        void configure(ResultSet rows) throws SQLException;
    }
}
