package com.memoirist.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.IntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * What the app finds in Postgres after it starts up on an empty database in the {@code test} profile: V1 applied, the
 * dev user seeded, and the columns that the decisions (D-xx) added (task 2.3).
 *
 * <p>The database is a fresh Testcontainers Postgres, so Flyway's work at startup <em>is</em> "apply the migrations to an
 * empty database". {@code MigrationsTest} covers the other locations, and {@code SchemaConstraintsTest} covers the rules.
 */
@IntegrationTest
class SchemaTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Flyway flyway;

    @Test
    void flywayAppliedV1AndTheSeedWithoutFailures() {
        assertThat(jdbc.sql("SELECT script FROM flyway_schema_history WHERE success ORDER BY installed_rank")
                        .query(String.class)
                        .list())
                .containsExactly("V1__core_schema.sql", "R__seed_dev_user.sql");
        assertThat(jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE NOT success")
                        .query(Long.class)
                        .single())
                .isZero();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void theDevUserIsSeeded() {
        record Row(String email, String displayName, String timezone, LocalDate birthday) {}

        Row dev = jdbc.sql("SELECT email, display_name, timezone, birthday FROM users WHERE id = :id")
                .param("id", UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .query(Row.class)
                .single();

        assertThat(dev).isEqualTo(new Row("dev@memoirist.local", "Dev User", "Australia/Melbourne", null));
        assertThat(jdbc.sql("SELECT count(*) FROM users").query(Long.class).single())
                .as("the dev user is the only user")
                .isEqualTo(1L);
    }

    @Test
    void thereAreExactlyThePhase1Tables() {
        // No week-Era table (D-22): a week's Eras are worked out from dates. Later phases add their own tables.
        assertThat(jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                        """).query(String.class).list())
                .containsExactlyInAnyOrder(
                        "users",
                        "eras",
                        "templates",
                        "weekly_logs",
                        "entries",
                        "media",
                        "tags",
                        "entry_tags",
                        "flyway_schema_history");
    }

    /**
     * The columns that decisions added to the first sketch of the schema, with the type each one needs. Postgres
     * reports a column's type in {@code information_schema.columns}; {@code length} is the VARCHAR size, if any.
     */
    @ParameterizedTest(name = "{0}.{1} is {2}, nullable={3}")
    @CsvSource(textBlock = """
            # table,       column,            type,                        nullable, length
            users,         birthday,          date,                        YES,
            eras,          soundtrack,        character varying,           YES,      100
            eras,          cover_media_id,    uuid,                        YES,
            eras,          pinned_in_feed,    boolean,                     NO,
            eras,          end_date,          date,                        YES,
            weekly_logs,   soundtrack,        character varying,           YES,      100
            weekly_logs,   is_closed,         boolean,                     NO,
            templates,     subheadings,       jsonb,                       NO,
            entries,       title,             character varying,           YES,      150
            entries,       entry_date,        date,                        NO,
            entries,       content_json,      jsonb,                       YES,
            entries,       preview_line,      character varying,           YES,      120
            entries,       soundtrack,        character varying,           YES,      100
            entries,       reply_to_entry_id, uuid,                        YES,
            entries,       unlock_date,       timestamp with time zone,    YES,
            entries,       unlock_seen_at,    timestamp with time zone,    YES,
            entries,       deleted_at,        timestamp with time zone,    YES,
            entries,       date_unknown,      boolean,                     NO,
            entries,       version,           bigint,                      NO,
            media,         purpose,           character varying,           NO,       20
            media,         display_order,     integer,                     NO,
            media,         file_size_bytes,   bigint,                      NO,
            media,         uploaded_at,       timestamp with time zone,    NO,
            entry_tags,    source,            character varying,           NO,       10
            """)
    void columnHasTheTypeTheSchemaDocSays(String table, String column, String type, String nullable, Integer length) {
        record Col(String type, String nullable, Integer length) {}

        Col actual = jdbc.sql("""
                        SELECT data_type AS type, is_nullable AS nullable, character_maximum_length AS length
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = :table AND column_name = :column
                        """)
                .param("table", table)
                .param("column", column)
                .query(Col.class)
                .single();

        assertThat(actual).isEqualTo(new Col(type, nullable, length));
    }

    @Test
    void noColumnBreaksTheTypeRules() {
        // Non-negotiable 2 and the schema conventions: TIMESTAMPTZ (never TIMESTAMP), VARCHAR (never CHAR, which
        // Hibernate's validator rejects as bpchar), JSONB (never JSON). Flyway's own history table is exempt.
        assertThat(jdbc.sql("""
                        SELECT table_name || '.' || column_name FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                          AND data_type IN ('timestamp without time zone', 'character', 'json')
                        """).query(String.class).list()).isEmpty();
    }
}
