package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.SqlLog;
import com.memoirist.tag.Tag;
import com.memoirist.tag.TagRepository;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * The entry finders (task 2.4): they only find the current user's entries, they leave out entries in Recently deleted
 * (D-30), and Recently deleted stays reachable through its own queries.
 *
 * <p>Every test runs in a transaction that is rolled back at the end. {@code em.flush()} writes pending changes to
 * Postgres and {@code em.clear()} empties the persistence context, so the next query reads the database, not memory.
 */
@IntegrationTest
@Transactional
class EntryRepositoryTest {

    private static final Instant DELETED_FIRST = Instant.parse("2026-10-20T01:00:00Z");
    private static final Instant DELETED_SECOND = Instant.parse("2026-10-21T01:00:00Z");

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    UserRepository users;

    @Autowired
    WeeklyLogRepository weeks;

    @Autowired
    TagRepository tags;

    @Autowired
    EntryRepository entries;

    User alice;
    User bob;
    WeeklyLog aliceWeek;
    WeeklyLog bobWeek;

    @BeforeEach
    void twoUsersEachWithAWeek() {
        alice = users.save(Fixtures.user());
        bob = users.save(Fixtures.user());
        aliceWeek = weeks.save(Fixtures.week(alice.getId()));
        bobWeek = weeks.save(Fixtures.week(bob.getId()));
        em.flush();
    }

    // TODO(human) PRACTICE: Prove that Bob cannot read Alice's entry through any entry finder. This is the test behind
    //   non-negotiable 3: a resource the user doesn't own is "not found", and the API turns that into a 404.
    //   Write it so it can fail for the right reason: show that Alice's own lookup DOES find the entry (otherwise an
    //   empty result for Bob proves nothing), then try Bob against each finder that takes a user id, and a random id.
    // Hint: security.md (Ownership). em.flush() then em.clear() makes the finders read Postgres, not the persistence
    //   context. Which assertion style names the finder that failed, so you don't have to guess from the stack trace?
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    @Test
    void anotherUsersEntryIsNotFoundByAnyFinder() {
        Entry entry = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        em.flush();
        em.clear();

        // Alice's own lookups find it. Without this, the empty results below would prove nothing.
        assertThat(entries.findByIdAndUserId(entry.getId(), alice.getId()))
                .as("Alice, findByIdAndUserId")
                .isPresent();
        assertThat(entries.findByIdInAndUserId(List.of(entry.getId()), alice.getId()))
                .as("Alice, findByIdInAndUserId")
                .hasSize(1);
        assertThat(entries.findByWeeklyLogIdAndUserIdOrderByEntryDateAscCreatedAtAsc(aliceWeek.getId(), alice.getId()))
                .as("Alice, the week's entries")
                .hasSize(1);

        // Bob gets nothing back, from the same id and from the same week.
        assertThat(entries.findByIdAndUserId(entry.getId(), bob.getId()))
                .as("Bob, findByIdAndUserId")
                .isEmpty();
        assertThat(entries.findByIdInAndUserId(List.of(entry.getId()), bob.getId()))
                .as("Bob, findByIdInAndUserId")
                .isEmpty();
        assertThat(entries.findByWeeklyLogIdAndUserIdOrderByEntryDateAscCreatedAtAsc(aliceWeek.getId(), bob.getId()))
                .as("Bob, Alice's week")
                .isEmpty();

        // An id that exists nowhere looks exactly the same as one that belongs to someone else.
        assertThat(entries.findByIdAndUserId(UUID.randomUUID(), alice.getId())).isEmpty();
    }
    // └── END ANSWER ▲ ──

    @Test
    void entriesInRecentlyDeletedAreLeftOutOfEveryFinder() {
        Entry live = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Entry deleted = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        deleted.setDeletedAt(DELETED_FIRST);
        em.flush();
        em.clear();

        assertThat(entries.findByIdAndUserId(deleted.getId(), alice.getId())).isEmpty();
        assertThat(entries.findByIdAndUserId(live.getId(), alice.getId())).isPresent();
        assertThat(entries.findByIdInAndUserId(List.of(live.getId(), deleted.getId()), alice.getId()))
                .extracting(Entry::getId)
                .containsExactly(live.getId());
        assertThat(entries.findByWeeklyLogIdAndUserIdOrderByEntryDateAscCreatedAtAsc(aliceWeek.getId(), alice.getId()))
                .extracting(Entry::getId)
                .containsExactly(live.getId());
    }

    @Test
    void theRestrictionAlsoCoversLoadsByIdThatNoFinderMakes() {
        // @SQLRestriction is on the entity, so even em.find() and a lazy reference can't reach a deleted entry. A
        // finder that forgets "deleted_at IS NULL" is therefore not possible to write by accident, which is the
        // reason it was chosen over a DeletedAtIsNull suffix on every finder.
        Entry deleted = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        deleted.setDeletedAt(DELETED_FIRST);
        em.flush();
        em.clear();

        assertThat(em.find(Entry.class, deleted.getId())).isNull();
    }

    @Test
    void recentlyDeletedIsReadThroughItsOwnQueriesNewestDeletionFirst() {
        Entry live = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Entry first = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Entry second = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Entry bobsDeleted = entries.save(Fixtures.entry(bob.getId(), bobWeek));
        first.setDeletedAt(DELETED_FIRST);
        second.setDeletedAt(DELETED_SECOND);
        bobsDeleted.setDeletedAt(DELETED_SECOND);
        em.flush();
        em.clear();

        assertThat(entries.findDeletedPageByUserId(alice.getId(), PageRequest.of(0, 10)))
                .extracting(Entry::getId)
                .as("Alice's list: both of hers, latest deletion first, not the live one and not Bob's")
                .containsExactly(second.getId(), first.getId());

        Entry found =
                entries.findDeletedByIdAndUserId(first.getId(), alice.getId()).orElseThrow();
        assertThat(found.getDeletedAt()).isEqualTo(DELETED_FIRST);
        assertThat(found.isNew())
                .as("a native query still goes through @PostLoad")
                .isFalse();

        assertThat(entries.findDeletedByIdAndUserId(live.getId(), alice.getId()))
                .as("a live entry is not in Recently deleted")
                .isEmpty();
        assertThat(entries.findDeletedByIdAndUserId(bobsDeleted.getId(), alice.getId()))
                .as("another user's deleted entry")
                .isEmpty();
    }

    @Test
    void restoringAnEntryPutsItBackInEveryFinder() {
        Entry entry = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        em.flush();
        entry.setDeletedAt(DELETED_FIRST);
        em.flush();
        em.clear();

        Entry deleted =
                entries.findDeletedByIdAndUserId(entry.getId(), alice.getId()).orElseThrow();
        deleted.setDeletedAt(null);
        em.flush();
        em.clear();

        assertThat(entries.findByIdAndUserId(entry.getId(), alice.getId())).isPresent();
        assertThat(entries.findDeletedByIdAndUserId(entry.getId(), alice.getId()))
                .isEmpty();
        assertThat(entries.findByIdAndUserId(entry.getId(), alice.getId())
                        .orElseThrow()
                        .getVersion())
                .as("deleting and restoring are both edits, so the version has gone up once for each save")
                .isEqualTo(2L);
    }

    @Test
    void aSealedEntryIsStillFoundBecauseTheMapperHidesItNotTheRepository() {
        // D-10: the feed lists a Loot Drop as a locked row, so the finders return it. EntryMapper redacts it. A count
        // or filter that must ignore sealed entries (tag filters, search) says so in its own query.
        Entry sealed = Fixtures.entry(alice.getId(), aliceWeek);
        sealed.setUnlockDate(Instant.parse("2099-01-01T00:00:00Z"));
        entries.save(sealed);
        em.flush();
        em.clear();

        assertThat(entries.findByIdAndUserId(sealed.getId(), alice.getId()))
                .get()
                .extracting(Entry::getUnlockDate)
                .isEqualTo(Instant.parse("2099-01-01T00:00:00Z"));
    }

    @Test
    void tagsAreLinkedThroughTheEntryTagsTableAndTheDatabaseFillsItsOtherColumns() {
        Tag work = tags.save(Fixtures.tag(alice.getId(), "Work"));
        Tag family = tags.save(Fixtures.tag(alice.getId(), "Family"));
        Entry entry = Fixtures.entry(alice.getId(), aliceWeek);
        entry.replaceTags(List.of(work, family));
        entries.save(entry);
        em.flush();
        em.clear();

        assertThat(entries.findByIdAndUserId(entry.getId(), alice.getId())
                        .orElseThrow()
                        .getTags())
                .extracting(Tag::getName)
                .containsExactlyInAnyOrder("Work", "Family");
        assertThat(jdbc.sql("SELECT source || ':' || (created_at IS NOT NULL) FROM entry_tags WHERE entry_id = :id")
                        .param("id", entry.getId())
                        .query(String.class)
                        .list())
                .as("source and created_at come from the column defaults; Phase 4 needs an EntryTag entity for 'AI'")
                .containsExactly("USER:true", "USER:true");
    }

    @Test
    void changingTagsWritesOnlyTheRowsThatChanged() {
        Tag work = tags.save(Fixtures.tag(alice.getId(), "Work"));
        Tag family = tags.save(Fixtures.tag(alice.getId(), "Family"));
        Tag travel = tags.save(Fixtures.tag(alice.getId(), "Travel"));
        Entry entry = Fixtures.entry(alice.getId(), aliceWeek);
        entry.replaceTags(List.of(work, family));
        entries.save(entry);
        em.flush();
        em.clear();

        Entry loaded = entries.findByIdAndUserId(entry.getId(), alice.getId()).orElseThrow();
        List<Tag> newTags = tags.findByIdInAndUserId(List.of(family.getId(), travel.getId()), alice.getId());
        try (SqlLog sql = SqlLog.capture()) {
            loaded.replaceTags(newTags);
            em.flush();

            assertThat(sql.statementsStartingWith("delete from entry_tags"))
                    .as("only Work is unlinked, not every tag and then re-added")
                    .hasSize(1)
                    .allMatch(statement -> statement.contains("tag_id=?"));
            assertThat(sql.statementsStartingWith("insert into entry_tags"))
                    .as("only Travel is linked")
                    .hasSize(1);
        }
    }

    @Test
    void deletingATagUnlinksItFromEveryEntry() {
        Tag work = tags.save(Fixtures.tag(alice.getId(), "Work"));
        Entry entry = Fixtures.entry(alice.getId(), aliceWeek);
        entry.replaceTags(List.of(work));
        entries.save(entry);
        em.flush();
        em.clear();

        tags.delete(tags.findByIdAndUserId(work.getId(), alice.getId()).orElseThrow());
        em.flush();
        em.clear();

        assertThat(entries.findByIdAndUserId(entry.getId(), alice.getId())
                        .orElseThrow()
                        .getTags())
                .as("the entry stays; ON DELETE CASCADE removed only the link row")
                .isEmpty();
    }

    // Recently deleted: the queries behind the page, the lock and the sweep (task 3.11, D-30)

    @Test
    void theDeletedPageHasATotalOrderAndCountsOnlyTheUsersDeletedEntries() {
        Entry live = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        List<Entry> sameInstant = List.of(
                entries.save(Fixtures.entry(alice.getId(), aliceWeek)),
                entries.save(Fixtures.entry(alice.getId(), aliceWeek)),
                entries.save(Fixtures.entry(alice.getId(), aliceWeek)));
        Entry older = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Entry bobsDeleted = entries.save(Fixtures.entry(bob.getId(), bobWeek));
        sameInstant.forEach(entry -> entry.setDeletedAt(DELETED_SECOND));
        older.setDeletedAt(DELETED_FIRST);
        bobsDeleted.setDeletedAt(DELETED_SECOND);
        em.flush();
        em.clear();

        Page<Entry> firstPage = entries.findDeletedPageByUserId(alice.getId(), PageRequest.of(0, 2));
        Page<Entry> secondPage = entries.findDeletedPageByUserId(alice.getId(), PageRequest.of(1, 2));

        assertThat(firstPage.getTotalElements())
                .as("Alice's four, not the live one and not Bob's")
                .isEqualTo(4);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        // Postgres orders a uuid by its bytes, unsigned. Java's UUID.compareTo reads the two halves as signed numbers,
        // so
        // it puts an id that starts with 8 to f first; the lower-case text of the id sorts the way Postgres does.
        List<UUID> tied = sameInstant.stream()
                .map(Entry::getId)
                .sorted(java.util.Comparator.comparing(UUID::toString).reversed())
                .toList();
        assertThat(firstPage.getContent())
                .extracting(Entry::getId)
                .as("the same instant: the id decides, newest id first")
                .containsExactly(tied.get(0), tied.get(1));
        assertThat(secondPage.getContent()).extracting(Entry::getId).containsExactly(tied.get(2), older.getId());
        assertThat(firstPage.getContent()).extracting(Entry::getId).doesNotContain(live.getId());
    }

    @Test
    void theLockedFinderFindsOnlyTheUsersOwnDeletedEntry() {
        Entry deleted = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Entry live = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        deleted.setDeletedAt(DELETED_FIRST);
        em.flush();
        em.clear();

        assertThat(entries.findDeletedForUpdateByIdAndUserId(deleted.getId(), alice.getId()))
                .as("Alice, her deleted entry")
                .isPresent();
        assertThat(entries.findDeletedForUpdateByIdAndUserId(deleted.getId(), bob.getId()))
                .as("Bob, Alice's deleted entry")
                .isEmpty();
        assertThat(entries.findDeletedForUpdateByIdAndUserId(live.getId(), alice.getId()))
                .as("Alice, a live entry of hers")
                .isEmpty();
    }

    @Test
    void theSweepListIsStrictlyBeforeTheCutoffAcrossUsersOldestFirst() {
        Entry aliceOld = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Entry bobOlder = entries.save(Fixtures.entry(bob.getId(), bobWeek));
        Entry onTheLine = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Entry young = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Entry live = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Instant cutoff = Instant.parse("2026-10-22T00:00:00Z");
        bobOlder.setDeletedAt(cutoff.minusSeconds(3600));
        aliceOld.setDeletedAt(cutoff.minusSeconds(1));
        onTheLine.setDeletedAt(cutoff);
        young.setDeletedAt(cutoff.plusSeconds(1));
        em.flush();
        em.clear();

        assertThat(entries.findDeletedIdsBefore(cutoff))
                .as("both users, the one that waited longest first, and not the one exactly on the line")
                .containsExactly(bobOlder.getId(), aliceOld.getId());
        assertThat(entries.findDeletedIdsBefore(cutoff.plusSeconds(1)))
                .as("a second later, the entry on the line is due too")
                .containsExactly(bobOlder.getId(), aliceOld.getId(), onTheLine.getId());
        assertThat(live.getDeletedAt()).isNull();
    }

    @Test
    void theSweepFinderFindsAnEntryOnlyWhileItIsStillDue() {
        Entry due = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Entry restored = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Entry young = entries.save(Fixtures.entry(alice.getId(), aliceWeek));
        Instant cutoff = Instant.parse("2026-10-22T00:00:00Z");
        due.setDeletedAt(cutoff.minusSeconds(1));
        restored.setDeletedAt(cutoff.minusSeconds(1));
        young.setDeletedAt(cutoff.plusSeconds(1));
        em.flush();
        em.clear();
        restored = entries.findDeletedByIdAndUserId(restored.getId(), alice.getId())
                .orElseThrow();
        restored.setDeletedAt(null);
        em.flush();
        em.clear();

        assertThat(entries.findDeletedForSweep(due.getId(), cutoff))
                .as("still due")
                .isPresent();
        assertThat(entries.findDeletedForSweep(restored.getId(), cutoff))
                .as("restored since the list was read")
                .isEmpty();
        assertThat(entries.findDeletedForSweep(young.getId(), cutoff))
                .as("not 30 days yet")
                .isEmpty();
        assertThat(entries.findDeletedForSweep(UUID.randomUUID(), cutoff))
                .as("gone")
                .isEmpty();
    }
}
