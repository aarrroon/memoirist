package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two queries behind the Opened strip (task 3.10): {@link EntryRepository#findOpenedUnseen} and
 * {@link EntryRepository#markSeen}, without the HTTP layer. The API tests ({@link EntryOpenedTest},
 * {@link EntryMarkSeenTest}) show the behaviour; these pin the SQL rules one at a time, in particular the ones the
 * API can't reach: a bulk {@code UPDATE} that must still obey the owner, the Recently deleted restriction and the
 * boundary instant.
 *
 * <p>Every test runs in a transaction that is rolled back at the end ({@code em.flush()} writes, {@code em.clear()}
 * empties the persistence context so the next read comes from Postgres), like {@link EntryRepositoryTest}.
 */
@IntegrationTest
@Transactional
class EntryOpenedRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

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
    }

    /** Alice's entry, unlocked at this instant ({@code null} for one that was never sealed), saved and flushed. */
    private Entry aliceEntryUnlockedAt(Instant unlockDate) {
        Entry entry = Fixtures.entry(alice.getId(), aliceWeek);
        entry.setUnlockDate(unlockDate);
        entries.save(entry);
        em.flush();
        return entry;
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.sql("SELECT * FROM entries WHERE id = :id")
                .param("id", id)
                .query()
                .singleRow();
    }

    // markSeen

    @Test
    void markSeenChangesOneRowTheFirstTimeAndNoneAfterwardsKeepingTheFirstTime() {
        Entry entry = aliceEntryUnlockedAt(NOW.minusSeconds(3600));

        int first = entries.markSeen(entry.getId(), alice.getId(), NOW);
        int second = entries.markSeen(entry.getId(), alice.getId(), NOW.plusSeconds(60));

        assertThat(first).as("the first call").isEqualTo(1);
        assertThat(second).as("a repeat").isZero();
        assertThat(((java.sql.Timestamp) row(entry.getId()).get("unlock_seen_at")).toInstant())
                .as("the time of the first call")
                .isEqualTo(NOW);
    }

    @Test
    void markSeenChangesNothingForAnotherUsersEntry() {
        Entry entry = aliceEntryUnlockedAt(NOW.minusSeconds(3600));

        int changed = entries.markSeen(entry.getId(), bob.getId(), NOW);

        assertThat(changed).isZero();
        assertThat(row(entry.getId()).get("unlock_seen_at")).isNull();
        // And Alice's own call still works afterwards, so the zero above was about Bob and not about the entry.
        assertThat(entries.markSeen(entry.getId(), alice.getId(), NOW)).isEqualTo(1);
    }

    @Test
    void markSeenIsAnOpenLootDropOnlyAndTheUnlockInstantItselfIsOpen() {
        Entry sealed = aliceEntryUnlockedAt(NOW.plusSeconds(1));
        Entry atTheInstant = aliceEntryUnlockedAt(NOW);
        Entry neverSealed = aliceEntryUnlockedAt(null);

        assertThat(entries.markSeen(sealed.getId(), alice.getId(), NOW))
                .as("one second before it opens")
                .isZero();
        assertThat(entries.markSeen(neverSealed.getId(), alice.getId(), NOW))
                .as("no unlock date at all")
                .isZero();
        assertThat(entries.markSeen(atTheInstant.getId(), alice.getId(), NOW))
                .as("exactly at the unlock instant")
                .isEqualTo(1);
        assertThat(row(sealed.getId()).get("unlock_seen_at")).isNull();
        assertThat(row(neverSealed.getId()).get("unlock_seen_at")).isNull();
    }

    @Test
    void markSeenLeavesAnEntryInRecentlyDeletedAlone() {
        Entry entry = aliceEntryUnlockedAt(NOW.minusSeconds(3600));
        entry.setDeletedAt(NOW.minusSeconds(60));
        em.flush();

        int changed = entries.markSeen(entry.getId(), alice.getId(), NOW);

        // Hibernate adds the entity's @SQLRestriction to a bulk update as it does to a select; this is the proof.
        assertThat(changed).isZero();
        assertThat(row(entry.getId()).get("unlock_seen_at")).isNull();
    }

    @Test
    void markSeenDoesNotTouchVersionOrUpdatedAt() {
        Entry entry = aliceEntryUnlockedAt(NOW.minusSeconds(3600));
        Map<String, Object> before = row(entry.getId());

        entries.markSeen(entry.getId(), alice.getId(), NOW);

        Map<String, Object> after = row(entry.getId());
        assertThat(after.get("unlock_seen_at")).as("the mark").isNotNull();
        assertThat(after.get("version")).as("version").isEqualTo(before.get("version"));
        assertThat(after.get("updated_at")).as("updated_at").isEqualTo(before.get("updated_at"));
    }

    // findOpenedUnseen

    @Test
    void findOpenedUnseenListsOnlyTheUsersOpenUnseenLootDropsMostRecentlyOpenedFirst() {
        Entry oldest = aliceEntryUnlockedAt(NOW.minusSeconds(3 * 86400));
        Entry newest = aliceEntryUnlockedAt(NOW.minusSeconds(60));
        Entry middle = aliceEntryUnlockedAt(NOW.minusSeconds(86400));
        // None of these may come back.
        aliceEntryUnlockedAt(NOW.plusSeconds(60)); // still sealed
        aliceEntryUnlockedAt(null); // never sealed
        Entry seen = aliceEntryUnlockedAt(NOW.minusSeconds(120));
        seen.setUnlockSeenAt(NOW.minusSeconds(30));
        Entry deleted = aliceEntryUnlockedAt(NOW.minusSeconds(180));
        deleted.setDeletedAt(NOW.minusSeconds(10));
        Entry bobs = Fixtures.entry(bob.getId(), bobWeek);
        bobs.setUnlockDate(NOW.minusSeconds(240));
        entries.save(bobs);
        em.flush();
        em.clear();

        List<Entry> strip = entries.findOpenedUnseen(alice.getId(), NOW, Limit.of(20));

        assertThat(strip).extracting(Entry::getId).containsExactly(newest.getId(), middle.getId(), oldest.getId());
    }

    @Test
    void findOpenedUnseenStopsAtTheLimitAndKeepsTheNewest() {
        Entry first = aliceEntryUnlockedAt(NOW.minusSeconds(300));
        Entry second = aliceEntryUnlockedAt(NOW.minusSeconds(200));
        aliceEntryUnlockedAt(NOW.minusSeconds(100));
        em.clear();

        List<Entry> strip = entries.findOpenedUnseen(alice.getId(), NOW, Limit.of(2));

        assertThat(strip)
                .hasSize(2)
                .extracting(Entry::getId)
                .doesNotContain(first.getId())
                .contains(second.getId());
    }
}
