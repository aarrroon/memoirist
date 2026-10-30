package com.memoirist.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memoirist.IntegrationTest;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rules the database enforces on its own, whatever the service layer does (task 2.3): CHECK and UNIQUE
 * constraints, column defaults and what each foreign key does when its parent row is deleted.
 *
 * <p>The services will validate first, with friendly errors. These tests prove the last line of defence behind them.
 * Every test runs in a transaction that is rolled back when it ends, so tests don't leave rows behind. A statement that
 * Postgres rejects aborts the whole transaction, which is why each rejected row is its own test.
 */
@IntegrationTest
@Transactional
class SchemaConstraintsTest {

    private static final String DEV_USER = "'00000000-0000-0000-0000-000000000001'";

    /** Rows that the bad-row statements can point at. {@link #fixtures()} inserts them before every test. */
    private static final String WEEK = "(SELECT id FROM weekly_logs WHERE start_date = DATE '2026-10-26')";

    private static final String ENTRY = "(SELECT id FROM entries WHERE entry_date = DATE '2026-10-28')";
    private static final String TAG = "(SELECT id FROM tags WHERE name = 'Work')";

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void fixtures() {
        sql("INSERT INTO weekly_logs (user_id, start_date, end_date) VALUES (" + DEV_USER
                + ", DATE '2026-10-26', DATE '2026-11-01')");
        sql("INSERT INTO entries (user_id, weekly_log_id, entry_date) VALUES (" + DEV_USER + ", " + WEEK
                + ", DATE '2026-10-28')");
        sql("INSERT INTO tags (user_id, name) VALUES (" + DEV_USER + ", 'Work')");
        sql("INSERT INTO media (entry_id, object_key, media_type, display_order, file_size_bytes) VALUES (" + ENTRY
                + ", 'users/dev/media-1.jpg', 'image/jpeg', 0, 1024)");
    }

    /**
     * Each row is a rule, one statement that breaks it, and the constraint Postgres should name. Statements that
     * break two rules at once would hide a missing constraint, so each one breaks exactly one.
     */
    static Stream<Arguments> badRows() {
        return Stream.of(
                Arguments.of(
                        "a week that starts on a Tuesday",
                        "INSERT INTO weekly_logs (user_id, start_date, end_date) VALUES (" + DEV_USER
                                + ", DATE '2026-10-27', DATE '2026-11-02')",
                        "chk_weekly_logs_monday"),
                Arguments.of(
                        "a week that lasts six days",
                        "INSERT INTO weekly_logs (user_id, start_date, end_date) VALUES (" + DEV_USER
                                + ", DATE '2026-11-02', DATE '2026-11-07')",
                        "chk_weekly_logs_seven_days"),
                Arguments.of(
                        "the same week twice for one user",
                        "INSERT INTO weekly_logs (user_id, start_date, end_date) VALUES (" + DEV_USER
                                + ", DATE '2026-10-26', DATE '2026-11-01')",
                        "uq_weekly_logs_user_start"),
                Arguments.of(
                        "an entry dated before 1900 (D-09)",
                        "INSERT INTO entries (user_id, weekly_log_id, entry_date) VALUES (" + DEV_USER + ", " + WEEK
                                + ", DATE '1899-12-31')",
                        "chk_entries_entry_date_min"),
                Arguments.of(
                        "an entry with a blank title",
                        "INSERT INTO entries (user_id, weekly_log_id, entry_date, title) VALUES (" + DEV_USER + ", "
                                + WEEK + ", DATE '2026-10-28', '   ')",
                        "chk_entries_title"),
                Arguments.of(
                        "an Era that ends before it starts",
                        "INSERT INTO eras (user_id, title, start_date, end_date) VALUES (" + DEV_USER
                                + ", 'Backwards', DATE '2026-02-01', DATE '2026-01-01')",
                        "chk_eras_dates"),
                Arguments.of(
                        "a template whose subheadings are an object, not an array",
                        "INSERT INTO templates (user_id, name, subheadings) VALUES (" + DEV_USER
                                + ", 'Bad', '{\"a\": 1}'::jsonb)",
                        "chk_templates_subheadings_array"),
                Arguments.of(
                        "the tag 'work' when 'Work' exists (case-insensitive, D-26)",
                        "INSERT INTO tags (user_id, name) VALUES (" + DEV_USER + ", 'work')",
                        "uq_tags_user_name"),
                Arguments.of(
                        "a blank tag name",
                        "INSERT INTO tags (user_id, name) VALUES (" + DEV_USER + ", '  ')",
                        "chk_tags_name"),
                Arguments.of(
                        "a tag link from a source other than USER or AI",
                        "INSERT INTO entry_tags (entry_id, tag_id, source) VALUES (" + ENTRY + ", " + TAG + ", 'BOT')",
                        "chk_entry_tags_source"),
                Arguments.of(
                        "media at a negative position",
                        "INSERT INTO media (entry_id, object_key, media_type, display_order, file_size_bytes) VALUES ("
                                + ENTRY + ", 'users/dev/media-2.jpg', 'image/jpeg', -1, 1024)",
                        "chk_media_display_order"),
                Arguments.of(
                        "media with zero bytes",
                        "INSERT INTO media (entry_id, object_key, media_type, display_order, file_size_bytes) VALUES ("
                                + ENTRY + ", 'users/dev/media-2.jpg', 'image/jpeg', 1, 0)",
                        "chk_media_file_size"),
                // The two UNIQUE constraints that V1 declares inline get Postgres's default names, not
                // uq_<table>_<cols> (journal 2.3, section 4). If a later migration renames them, this test says so.
                Arguments.of(
                        "a second user with the dev user's email",
                        "INSERT INTO users (email) VALUES ('dev@memoirist.local')",
                        "users_email_key"),
                Arguments.of(
                        "two media rows with the same object key",
                        "INSERT INTO media (entry_id, object_key, media_type, display_order, file_size_bytes) VALUES ("
                                + ENTRY + ", 'users/dev/media-1.jpg', 'image/jpeg', 1, 1024)",
                        "media_object_key_key"),
                Arguments.of(
                        "an entry for a user who doesn't exist",
                        "INSERT INTO entries (user_id, weekly_log_id, entry_date) VALUES ('" + UUID.randomUUID() + "', "
                                + WEEK + ", DATE '2026-10-28')",
                        "entries_user_id_fkey"));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("badRows")
    void theDatabaseRejects(String rule, String badStatement, String constraint) {
        // TODO(human) PRACTICE: Prove that Postgres itself rejects this row, and that it is the constraint named in
        //   the table that rejects it. Write the call and the assertions with JdbcClient and AssertJ.
        // Hint: a row can break more than one rule, and the app's services will validate before a row gets this far,
        //   so this test guards the schema itself. Which exception type does Spring throw for a CHECK, UNIQUE or
        //   foreign-key violation, and where in the message does Postgres put the constraint's name? A test that only
        //   checks the exception type would still pass if the wrong constraint fired.
        // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
        assertThatThrownBy(() -> jdbc.sql(badStatement).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("\"" + constraint + "\"");
        // └── END ANSWER ▲ ──
    }

    @Test
    void minimalRowsGetTheDocumentedDefaults() {
        sql("INSERT INTO users (email) VALUES ('defaults@example.test')");
        sql("INSERT INTO eras (user_id, title, start_date) SELECT id, 'Era', DATE '2026-01-01' FROM users"
                + " WHERE email = 'defaults@example.test'");
        sql("INSERT INTO templates (user_id, name) SELECT id, 'Template' FROM users"
                + " WHERE email = 'defaults@example.test'");
        sql("INSERT INTO entry_tags (entry_id, tag_id) VALUES (" + ENTRY + ", " + TAG + ")");

        assertThat(jdbc.sql("""
                        SELECT display_name || '|' || timezone || '|' || coalesce(birthday::text, 'none')
                        FROM users WHERE email = 'defaults@example.test'
                        """).query(String.class).single()).isEqualTo("Time Traveler|Australia/Melbourne|none");
        assertThat(jdbc.sql("SELECT pinned_in_feed::text || '|' || coalesce(end_date::text, 'ongoing') FROM eras"
                                + " WHERE title = 'Era'")
                        .query(String.class)
                        .single())
                .as("a new Era is not pinned and is ongoing")
                .isEqualTo("false|ongoing");
        assertThat(jdbc.sql("SELECT subheadings::text FROM templates WHERE name = 'Template'")
                        .query(String.class)
                        .single())
                .isEqualTo("[]");
        assertThat(jdbc.sql("SELECT is_closed::text FROM weekly_logs WHERE start_date = DATE '2026-10-26'")
                        .query(String.class)
                        .single())
                .isEqualTo("false");
        assertThat(jdbc.sql("""
                        SELECT version || '|' || unlock_notified || '|' || date_unknown || '|' || '[' || content_text
                               || ']|' || (content_json IS NULL) || '|' || (unlock_date IS NULL)
                               || '|' || (unlock_seen_at IS NULL) || '|' || (deleted_at IS NULL)
                        FROM entries WHERE entry_date = DATE '2026-10-28'
                        """).query(String.class).single())
                .as("version 0, nothing notified, date known, empty text, not sealed, not seen, not deleted")
                .isEqualTo("0|false|false|[]|true|true|true|true");
        assertThat(jdbc.sql("SELECT purpose FROM media WHERE object_key = 'users/dev/media-1.jpg'")
                        .query(String.class)
                        .single())
                .isEqualTo("PHOTO");
        assertThat(jdbc.sql("SELECT source FROM entry_tags").query(String.class).single())
                .isEqualTo("USER");
    }

    @Test
    void deletingAUserDeletesEverythingTheyOwnEvenThroughTheEraCoverCycle() {
        // eras -> media (cover) -> entries -> users -> eras is a loop of foreign keys. Postgres must still be able to
        // delete the whole graph from the user down.
        sql("INSERT INTO users (id, email) VALUES ('11111111-1111-1111-1111-111111111111', 'gone@example.test')");
        sql("INSERT INTO weekly_logs (id, user_id, start_date, end_date) VALUES"
                + " ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',"
                + " DATE '2026-10-26', DATE '2026-11-01')");
        sql("INSERT INTO entries (id, user_id, weekly_log_id, entry_date) VALUES"
                + " ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111',"
                + " '22222222-2222-2222-2222-222222222222', DATE '2026-10-28')");
        sql("INSERT INTO media (id, entry_id, object_key, media_type, display_order, file_size_bytes) VALUES"
                + " ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333',"
                + " 'users/gone/media.jpg', 'image/jpeg', 0, 1)");
        sql("INSERT INTO eras (id, user_id, title, start_date, cover_media_id) VALUES"
                + " ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'Era',"
                + " DATE '2026-01-01', '44444444-4444-4444-4444-444444444444')");
        sql("INSERT INTO tags (id, user_id, name) VALUES"
                + " ('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', 'gone')");
        sql("INSERT INTO entry_tags (entry_id, tag_id) VALUES"
                + " ('33333333-3333-3333-3333-333333333333', '66666666-6666-6666-6666-666666666666')");
        sql("INSERT INTO templates (user_id, name) VALUES ('11111111-1111-1111-1111-111111111111', 'gone')");

        int deleted = sql("DELETE FROM users WHERE id = '11111111-1111-1111-1111-111111111111'");

        assertThat(deleted).isEqualTo(1);
        for (String table : new String[] {"weekly_logs", "entries", "eras", "tags", "templates"}) {
            assertThat(count("SELECT count(*) FROM " + table
                            + " WHERE user_id = '11111111-1111-1111-1111-111111111111'"))
                    .as("rows left in %s", table)
                    .isZero();
        }
        assertThat(count("SELECT count(*) FROM media WHERE object_key = 'users/gone/media.jpg'"))
                .as("media left")
                .isZero();
        assertThat(count("SELECT count(*) FROM entry_tags WHERE tag_id = '66666666-6666-6666-6666-666666666666'"))
                .as("entry_tags left")
                .isZero();
        // The dev user's own rows, set up in fixtures(), are untouched.
        assertThat(count("SELECT count(*) FROM entries WHERE user_id = " + DEV_USER))
                .isEqualTo(1);
    }

    @Test
    void deletingAnEntryDeletesItsMediaAndTagLinksButOnlyDetachesRepliesAndEraCovers() {
        sql("INSERT INTO entry_tags (entry_id, tag_id) VALUES (" + ENTRY + ", " + TAG + ")");
        sql("INSERT INTO eras (user_id, title, start_date, cover_media_id) VALUES (" + DEV_USER + ", 'Era',"
                + " DATE '2026-01-01', (SELECT id FROM media WHERE object_key = 'users/dev/media-1.jpg'))");
        sql("INSERT INTO entries (user_id, weekly_log_id, entry_date, reply_to_entry_id) VALUES (" + DEV_USER + ", "
                + WEEK + ", DATE '2026-10-29', " + ENTRY + ")");

        sql("DELETE FROM entries WHERE entry_date = DATE '2026-10-28'");

        assertThat(count("SELECT count(*) FROM media"))
                .as("media cascades with its entry")
                .isZero();
        assertThat(count("SELECT count(*) FROM entry_tags"))
                .as("tag links cascade with their entry")
                .isZero();
        assertThat(count("SELECT count(*) FROM tags"))
                .as("the tag itself stays")
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM eras WHERE title = 'Era' AND cover_media_id IS NULL"))
                .as("the Era stays but loses its cover (SET NULL)")
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM entries WHERE entry_date = DATE '2026-10-29'"
                        + " AND reply_to_entry_id IS NULL"))
                .as("the reply stays but no longer points at the deleted entry (SET NULL)")
                .isEqualTo(1);
    }

    @Test
    void deletingATemplateLeavesItsEntriesWithoutOne() {
        sql("INSERT INTO templates (id, user_id, name) VALUES" + " ('77777777-7777-7777-7777-777777777777', " + DEV_USER
                + ", 'Gratitude')");
        sql("UPDATE entries SET template_id = '77777777-7777-7777-7777-777777777777'");

        sql("DELETE FROM templates WHERE id = '77777777-7777-7777-7777-777777777777'");

        assertThat(count("SELECT count(*) FROM entries WHERE template_id IS NULL"))
                .isEqualTo(1);
    }

    private int sql(String statement) {
        return jdbc.sql(statement).update();
    }

    private long count(String query) {
        return jdbc.sql(query).query(Long.class).single();
    }
}
