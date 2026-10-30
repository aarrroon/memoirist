package com.memoirist.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.SqlLog;
import com.memoirist.entry.Entry;
import com.memoirist.entry.EntryRepository;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Media and how an entry loads it (task 2.4): carousel order, one batched query for a page of entries, ownership
 * through the entry, and what happens to media when its entry is deleted or removed.
 */
@IntegrationTest
@Transactional
class MediaRepositoryTest {

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    UserRepository users;

    @Autowired
    WeeklyLogRepository weeks;

    @Autowired
    EntryRepository entries;

    @Autowired
    MediaRepository media;

    User alice;
    User bob;
    WeeklyLog aliceWeek;

    @BeforeEach
    void twoUsersAndAWeek() {
        alice = users.save(Fixtures.user());
        bob = users.save(Fixtures.user());
        aliceWeek = weeks.save(Fixtures.week(alice.getId()));
        em.flush();
    }

    @Test
    void anEntrysMediaComesBackInCarouselOrderWhateverOrderItWasSaved() {
        Entry entry = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Media third = media.save(Fixtures.photo(entry, 2));
        Media first = media.save(Fixtures.photo(entry, 0));
        Media second = media.save(Fixtures.photo(entry, 1));
        em.flush();
        em.clear();

        Entry loaded = entries.findByIdAndUserId(entry.getId(), alice.getId()).orElseThrow();

        try (SqlLog sql = SqlLog.capture()) {
            assertThat(loaded.getMedia())
                    .as("@OrderBy(\"displayOrder ASC\") on Entry.media")
                    .extracting(Media::getId)
                    .containsExactly(first.getId(), second.getId(), third.getId());
            // The values above could be right by luck: Postgres may read the rows through the unique index on
            // (entry_id, display_order), which happens to be in order. The SQL is what the annotation guarantees.
            assertThat(sql.statementsStartingWith("select"))
                    .singleElement()
                    .asString()
                    .contains("from media")
                    .contains("order by")
                    .contains("display_order");
        }
        assertThat(media.findByEntryIdAndEntryUserIdOrderByDisplayOrderAsc(entry.getId(), alice.getId()))
                .extracting(Media::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void aPageOfEntriesLoadsAllTheirMediaInOneQueryNotOnePerEntry() {
        // The N+1 problem, and the cure the sprint plan asks for: @BatchSize(size = 50) on Entry.media.
        List<Entry> saved = List.of(
                entries.save(Fixtures.entry(alice.getId(), aliceWeek)),
                entries.save(Fixtures.entry(alice.getId(), aliceWeek)),
                entries.save(Fixtures.entry(alice.getId(), aliceWeek)));
        for (Entry entry : saved) {
            media.save(Fixtures.photo(entry, 0));
            media.save(Fixtures.photo(entry, 1));
        }
        em.flush();
        em.clear();

        try (SqlLog sql = SqlLog.capture()) {
            List<Entry> page =
                    entries.findByIdInAndUserId(saved.stream().map(Entry::getId).toList(), alice.getId());
            int photos =
                    page.stream().mapToInt(entry -> entry.getMedia().size()).sum();

            assertThat(photos).isEqualTo(6);
            assertThat(sql.statementsStartingWith("select")).hasSize(2);
            assertThat(sql.statements().stream().filter(statement -> statement.contains("from media")))
                    .as("one query for the media of all three entries, not three")
                    .hasSize(1);
        }
    }

    @Test
    void mediaIsFoundOnlyThroughItsOwnersEntry() {
        Entry entry = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Media photo = media.save(Fixtures.photo(entry, 0));
        em.flush();
        em.clear();

        assertThat(media.findByIdAndEntryUserId(photo.getId(), alice.getId())).isPresent();
        assertThat(media.findByIdAndEntryUserId(photo.getId(), bob.getId()))
                .as("Bob, Alice's media id")
                .isEmpty();
        assertThat(media.findByEntryIdAndEntryUserIdOrderByDisplayOrderAsc(entry.getId(), bob.getId()))
                .as("Bob, Alice's entry")
                .isEmpty();
    }

    @Test
    void theMediaOfARecentlyDeletedEntryIsNotFoundSoNothingPresignsIt() {
        // security.md: an entry in Recently deleted is "404, never presigned". The finders reach media through the
        // entry, and the entry's @SQLRestriction applies to that join.
        Entry entry = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Media photo = media.save(Fixtures.photo(entry, 0));
        em.flush();
        entry.setDeletedAt(Instant.parse("2026-10-20T01:00:00Z"));
        em.flush();
        em.clear();

        assertThat(media.findByIdAndEntryUserId(photo.getId(), alice.getId()))
                .as("even for its owner")
                .isEmpty();
        assertThat(media.findByEntryIdAndEntryUserIdOrderByDisplayOrderAsc(entry.getId(), alice.getId()))
                .isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM media WHERE id = :id")
                        .param("id", photo.getId())
                        .query(Long.class)
                        .single())
                .as("the row is still there for restore and erase")
                .isEqualTo(1L);

        // Erase needs the object keys to delete the stored files. They are reachable from the deleted entry itself:
        // Entry.media loads by the entry_id column, with no join to entries, so the restriction doesn't get in the way.
        Entry deleted =
                entries.findDeletedByIdAndUserId(entry.getId(), alice.getId()).orElseThrow();
        assertThat(deleted.getMedia())
                .extracting(Media::getObjectKey)
                .as("the keys of the files that erase must delete")
                .containsExactly(photo.getObjectKey());

        // Restoring brings the media back.
        deleted.setDeletedAt(null);
        em.flush();
        em.clear();

        assertThat(media.findByIdAndEntryUserId(photo.getId(), alice.getId())).isPresent();
    }

    @Test
    void removingAnEntryLeavesItsMediaToTheDatabaseCascade() {
        // Entry.media has no cascade, so Hibernate doesn't load the photos just to delete them one by one. The
        // foreign key's ON DELETE CASCADE removes them, after the service has deleted the stored files (TECH_STACK §2).
        Entry entry = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        media.save(Fixtures.photo(entry, 0));
        media.save(Fixtures.photo(entry, 1));
        em.flush();
        em.clear();

        Entry loaded = entries.findByIdAndUserId(entry.getId(), alice.getId()).orElseThrow();
        try (SqlLog sql = SqlLog.capture()) {
            entries.delete(loaded);
            em.flush();

            assertThat(sql.statementsStartingWith("delete from entries")).hasSize(1);
            assertThat(sql.statements())
                    .as("Hibernate never touches the media table")
                    .noneMatch(statement -> statement.contains("media"));
        }
        assertThat(jdbc.sql("SELECT count(*) FROM media WHERE entry_id = :id")
                        .param("id", entry.getId())
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void uploadedAtIsStampedOnInsertAndNeverChanges() {
        Entry entry = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Media photo = media.save(Fixtures.photo(entry, 0));
        em.flush();
        Instant stamped = photo.getUploadedAt();
        assertThat(stamped).isNotNull();

        photo.setDisplayOrder(5);
        em.flush();
        em.clear();

        Media loaded =
                media.findByIdAndEntryUserId(photo.getId(), alice.getId()).orElseThrow();
        assertThat(loaded.getDisplayOrder()).isEqualTo(5);
        assertThat(loaded.getUploadedAt()).isEqualTo(stamped.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }
}
