package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.user.User;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * How many SQL statements the Opened strip and {@code mark-seen} run, counted by Hibernate's own {@link Statistics}
 * (the technique of {@code EntryListQueryCountTest}; see there). The strip is a list of rows like the timeline, so it
 * has the same promise: the cost does not grow with the number of Loot Drops, photos or tags.
 *
 * <p>What the strip costs (see {@code EntryListService.opened}):
 * <ol>
 *   <li>the query for the strip (at most 20 rows);
 *   <li>the media of those entries, and 3. their tags, one {@code left join fetch} each. Every entry in the strip is
 *       open, so there is no sealed entry to skip; a strip with nothing in it runs neither of the two.
 * </ol>
 * There is no count: the strip is a plain array, not a page.
 */
class EntryOpenedQueryCountTest extends EntryOpenedApiTest {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    /** The statements Hibernate prepared while {@code request} ran, and nothing else. */
    private long statementsDuring(Runnable request) {
        Statistics statistics = statistics();
        statistics.clear();
        request.run();
        return statistics.getPrepareStatementCount();
    }

    /** {@code drops} Loot Drops that have opened, each with {@code photos} photos, a voice note and two tags. */
    private void openedDrops(User user, int drops, int photos) {
        for (int i = 0; i < drops; i++) {
            String id = aLootDrop(user, "Drop " + i, OPENS_1);
            for (int photo = 0; photo < photos; photo++) {
                aPhoto(id, "users/test/strip/" + id + "/" + photo + ".jpg", photo);
            }
            aVoiceNote(id, "users/test/strip/" + id + "/voice.webm", photos);
            aTag(user, id, "Tag A " + i);
            aTag(user, id, "Tag B " + i);
        }
    }

    @Test
    void statisticsAreOnInTheTestProfile() {
        assertThat(statistics().isStatisticsEnabled()).isTrue();
    }

    @Test
    void theStripCostsTheSameNumberOfStatementsWhateverItHolds() {
        User small = aUser();
        openedDrops(small, 1, 1);
        User big = aUser();
        openedDrops(big, 15, 3);
        clockAt(AFTER_ALL);

        long smallCount = statementsDuring(() -> opened(small).expectStatus().isOk());
        long bigCount = statementsDuring(() -> opened(big).expectStatus().isOk());

        assertThat(bigCount)
                .as("15 Loot Drops with 3 photos, a voice note and 2 tags each cost what 1 costs")
                .isEqualTo(smallCount);
        assertThat(smallCount).as("the strip, the media and the tags").isEqualTo(3);
    }

    @Test
    void aFullStripOfTwentyCostsTheSameAsOneAndMoreThanTwentyAreCutNotCounted() {
        User full = aUser();
        openedDrops(full, 22, 1);
        clockAt(AFTER_ALL);

        long count = statementsDuring(() -> opened(full).expectStatus().isOk());

        assertThat(openedIds(full)).as("22 opened, 20 shown").hasSize(20);
        assertThat(count)
                .as("more than one Hibernate batch of 50 would not matter either")
                .isEqualTo(3);
    }

    @Test
    void aStripWithNothingInItRunsOnlyTheStripQuery() {
        User sealedOnly = aUser();
        aLootDrop(sealedOnly, "Not yet", OPENS_3);
        User nobodyYet = aUser();
        clockAt(OPENS_1);

        assertThat(statementsDuring(() -> opened(sealedOnly).expectStatus().isOk()))
                .as("a sealed Loot Drop's media and tags are never read")
                .isEqualTo(1);
        assertThat(statementsDuring(() -> opened(nobodyYet).expectStatus().isOk()))
                .isEqualTo(1);
    }

    @Test
    void markSeenIsOneLookupAndOneUpdateWhateverTheEntryHolds() {
        User alice = aUser();
        String bare = aLootDrop(alice, "Bare", OPENS_1);
        String heavy = aLootDrop(alice, "Heavy", OPENS_1);
        for (int i = 0; i < 8; i++) {
            aPhoto(heavy, "users/test/strip/" + heavy + "/" + i + ".jpg", i);
        }
        aTag(alice, heavy, "Tagged");
        clockAt(AFTER_ALL);

        long bareCount = statementsDuring(() -> markSeenOk(alice, bare));
        long heavyCount = statementsDuring(() -> markSeenOk(alice, heavy));

        assertThat(heavyCount).isEqualTo(bareCount);
        assertThat(bareCount).as("find the entry, then the conditional UPDATE").isEqualTo(2);
    }
}
