package com.memoirist.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.SqlLog;
import com.memoirist.entry.Entry;
import com.memoirist.entry.EntryRepository;
import com.memoirist.era.Era;
import com.memoirist.era.EraRepository;
import com.memoirist.media.Media;
import com.memoirist.media.MediaRepository;
import com.memoirist.tag.Tag;
import com.memoirist.tag.TagRepository;
import com.memoirist.template.Template;
import com.memoirist.template.TemplateRepository;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * What {@code save()} sends to Postgres for an entity whose id the application chose (D-12, task 2.4).
 *
 * <p>These tests read the SQL itself ({@link SqlLog}) instead of trusting the code. A new row must cost one INSERT.
 * Without {@link org.springframework.data.domain.Persistable}, Spring Data would call {@code merge}, and Hibernate
 * would run a SELECT first to find out whether the row exists.
 */
@IntegrationTest
@Transactional
class PersistableInsertTest {

    private static final Pattern INSERT_INTO = Pattern.compile("^insert into (\\w+) ");

    @Autowired
    EntityManager em;

    @Autowired
    UserRepository users;

    @Autowired
    WeeklyLogRepository weeks;

    @Autowired
    TemplateRepository templates;

    @Autowired
    EraRepository eras;

    @Autowired
    TagRepository tags;

    @Autowired
    EntryRepository entries;

    @Autowired
    MediaRepository media;

    @Test
    void savingAnyEntityWithAPresetIdIsOneInsertAndNoSelect() {
        // Build all seven first. Their ids are chosen here, in the constructors, like a client-supplied id (D-12).
        User user = Fixtures.user();
        WeeklyLog week = Fixtures.week(user.getId());
        Template template = new Template(UUID.randomUUID(), user.getId(), "Morning", List.of("Goals"));
        Era era = new Era(UUID.randomUUID(), user.getId(), "Uni", LocalDate.of(2026, 3, 1));
        Tag tag = Fixtures.tag(user.getId(), "Work");
        Entry entry = Fixtures.entry(user.getId(), week);
        Media photo = Fixtures.photo(entry, 0);

        try (SqlLog sql = SqlLog.capture()) {
            // In dependency order, because the foreign keys are checked as each INSERT runs.
            users.save(user);
            weeks.save(week);
            templates.save(template);
            eras.save(era);
            tags.save(tag);
            entries.save(entry);
            media.save(photo);
            em.flush();

            assertThat(sql.statements())
                    .as("every statement is an INSERT: a SELECT here would be Spring Data calling merge()")
                    .allMatch(statement -> statement.startsWith("insert into "));
            assertThat(tablesInserted(sql))
                    .as("one INSERT per saved entity, in the order they were saved")
                    .containsExactly("users", "weekly_logs", "templates", "eras", "tags", "entries", "media");
        }
    }

    @Test
    void mergeOfAnEntityWithAPresetIdSelectsFirst_whichIsWhatPersistableAvoids() {
        // The control experiment. If this passed with no SELECT too, the test above would prove nothing, because
        // SqlLog might simply be missing statements. Tag has no @Version, so Hibernate can only learn whether a row
        // with this id exists by asking the database. This is exactly what save() calls for an entity that says
        // it is not new, which is what Spring Data assumes for any entity whose id is already set.
        User user = users.save(Fixtures.user());
        em.flush();
        Tag tag = Fixtures.tag(user.getId(), "Work");

        try (SqlLog sql = SqlLog.capture()) {
            em.merge(tag);
            em.flush();

            assertThat(sql.statements()).hasSize(2);
            assertThat(sql.statements().get(0)).startsWith("select").contains("from tags");
            assertThat(sql.statements().get(1)).startsWith("insert into tags");
        }
    }

    @Test
    void anEntityIsNewUntilItIsSavedOrLoaded() {
        User user = users.save(Fixtures.user());
        WeeklyLog week = Fixtures.week(user.getId());
        assertThat(week.isNew()).as("just constructed").isTrue();

        weeks.save(week);
        em.flush();
        assertThat(week.isNew()).as("after the INSERT (@PostPersist)").isFalse();

        em.clear();
        WeeklyLog loaded = weeks.findByIdAndUserId(week.getId(), user.getId()).orElseThrow();
        assertThat(loaded).isNotSameAs(week);
        assertThat(loaded.isNew()).as("read from the database (@PostLoad)").isFalse();
    }

    @Test
    void savingALoadedEntityAgainIsOneUpdate() {
        User user = users.save(Fixtures.user());
        Tag tag = tags.save(Fixtures.tag(user.getId(), "Work"));
        em.flush();
        em.clear();

        Tag loaded = tags.findByIdAndUserId(tag.getId(), user.getId()).orElseThrow();
        loaded.setName("Work life");

        try (SqlLog sql = SqlLog.capture()) {
            tags.save(loaded);
            em.flush();

            assertThat(sql.statements()).hasSize(1);
            assertThat(sql.statements().get(0)).startsWith("update tags set");
        }
    }

    @Test
    void aDetachedEntryThatWasNeverEditedIsUpdatedNotInsertedWhenSavedAgain() {
        // A saved entry that was never edited is still at version 0, the number it had when it was new. Saving a
        // detached copy of it from another transaction must be an UPDATE of the same row, not a second INSERT of the
        // same id. What tells the two apart is isNew(): false after the first save, so save() calls merge(), not
        // persist().
        User user = users.save(Fixtures.user());
        WeeklyLog week = weeks.save(Fixtures.week(user.getId()));
        Entry entry = entries.save(Fixtures.entry(user.getId(), week));
        em.flush();
        assertThat(entry.getVersion()).isZero();
        em.detach(entry);

        entry.setTitle("Edited somewhere else");
        try (SqlLog sql = SqlLog.capture()) {
            entries.save(entry);
            em.flush();

            assertThat(sql.statementsStartingWith("insert")).isEmpty();
            assertThat(sql.statementsStartingWith("update entries")).hasSize(1);
        }
        assertThat(entry.getVersion())
                .as("the detached copy is not the saved one")
                .isZero();
    }

    /** The table of each INSERT, in order: {@code "insert into entries (...)"} gives {@code "entries"}. */
    private static List<String> tablesInserted(SqlLog sql) {
        return sql.statements().stream()
                .map(INSERT_INTO::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .toList();
    }
}
