package com.memoirist.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs Flyway by hand, with the same locations each profile uses, against an empty Postgres in Docker (task 2.3).
 *
 * <p>This is not a Spring test. It exists to prove what {@code SchemaTest} can't: the {@code prod} locations
 * ({@code db/migration} only) leave the dev user out (D-11), and the seed is safe to run again (D-16). Each test works in
 * a schema of its own inside one shared container, so every test starts from an empty database without paying for
 * another container.
 */
@Testcontainers
class MigrationsTest {

    private static final String SCHEMA_ONLY = "classpath:db/migration";
    private static final String DEV_SEED = "classpath:db/seed-local";
    private static final String DEV_USER_EMAIL = "dev@memoirist.local";

    /** The seeded dev user's ID (D-11), written out here so a change to the seed fails a test. */
    private static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestcontainersConfiguration.POSTGRES_IMAGE);

    @Test
    void theProductionLocationsCreateTheSchemaButNoDevUser() {
        String schema = freshSchemaName();

        MigrateResult result = flyway(schema, SCHEMA_ONLY).migrate();

        // One versioned migration, and nothing repeatable: the seed lives in a location prod never loads.
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(jdbc().sql("SELECT count(*) FROM %s.users".formatted(schema))
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(appliedScripts(schema)).containsExactly("V1__core_schema.sql");
    }

    @Test
    void theLocalLocationsAlsoSeedTheDevUser() {
        String schema = freshSchemaName();

        MigrateResult result = flyway(schema, SCHEMA_ONLY, DEV_SEED).migrate();

        // The versioned migration first, then the repeatable seed, whatever the file names sort like.
        assertThat(result.migrationsExecuted).isEqualTo(2);
        assertThat(appliedScripts(schema)).containsExactly("V1__core_schema.sql", "R__seed_dev_user.sql");
        assertThat(jdbc().sql("SELECT email FROM %s.users WHERE id = :id".formatted(schema))
                        .param("id", DEV_USER_ID)
                        .query(String.class)
                        .single())
                .isEqualTo(DEV_USER_EMAIL);
    }

    @Test
    void migratingAgainDoesNothingBecauseNeitherChecksumChanged() {
        String schema = freshSchemaName();
        flyway(schema, SCHEMA_ONLY, DEV_SEED).migrate();

        // A fresh Flyway object, as a restarted app would have. A repeatable migration runs again only when its
        // checksum has changed.
        MigrateResult second = flyway(schema, SCHEMA_ONLY, DEV_SEED).migrate();

        assertThat(second.migrationsExecuted).isZero();
    }

    @Test
    void theSeedScriptIsSafeToRunAgainOnADatabaseThatAlreadyHasTheDevUser() throws Exception {
        String schema = freshSchemaName();
        flyway(schema, SCHEMA_ONLY, DEV_SEED).migrate();
        String seedSql =
                new ClassPathResource("db/seed-local/R__seed_dev_user.sql").getContentAsString(StandardCharsets.UTF_8);

        // Flyway re-runs an R__ script whenever its checksum changes, so it must not fail or duplicate the row.
        int rowsInserted = jdbc(schema).sql(seedSql).update();

        assertThat(rowsInserted).isZero();
        assertThat(jdbc().sql("SELECT count(*) FROM %s.users".formatted(schema))
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
    }

    @Test
    void theSchemaAppliesToAnEmptyDatabaseAndLeavesNothingPending() {
        String schema = freshSchemaName();
        Flyway flyway = flyway(schema, SCHEMA_ONLY, DEV_SEED);

        flyway.migrate();

        assertThat(flyway.info().pending()).isEmpty();
        assertThat(List.of(flyway.info().applied()))
                .allSatisfy(migration ->
                        assertThat(migration.getState().isApplied()).isTrue());
    }

    /**
     * The SQL scripts Flyway ran in the schema, in the order it ran them. Flyway also writes a row of type SCHEMA when
     * it creates the schema itself, and that row is not a migration.
     */
    private static List<String> appliedScripts(String schema) {
        return jdbc().sql("SELECT script FROM %s.flyway_schema_history WHERE type = 'SQL' ORDER BY installed_rank"
                        .formatted(schema))
                .query(String.class)
                .list();
    }

    /** Flyway pointed at one schema; it creates the schema and keeps its history table there. */
    private static Flyway flyway(String schema, String... locations) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .locations(locations)
                .load();
    }

    private static String freshSchemaName() {
        // Postgres folds unquoted names to lower case, so keep it lower case and free of dashes.
        return "t_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static JdbcClient jdbc() {
        return JdbcClient.create(dataSource());
    }

    /** A client whose unqualified table names resolve in the given schema (what the app's search_path does). */
    private static JdbcClient jdbc(String schema) {
        DriverManagerDataSource dataSource = dataSource();
        dataSource.setSchema(schema);
        return JdbcClient.create(dataSource);
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
