package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.user.User;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * How many SQL statements the On this day strip runs, counted by Hibernate's own {@link Statistics} (the technique of
 * {@code EntryListQueryCountTest}). Like the other lists it promises that the cost does not grow with the number of
 * entries, photos or tags.
 *
 * <p>What the strip costs (see {@code EntryListService.onThisDay}):
 * <ol>
 *   <li>the user, because the time zone that decides today is on the user;
 *   <li>the query for the strip (at most 20 rows);
 *   <li>the media of those entries, and 4. their tags, one {@code left join fetch} each. Every row is open (a sealed
 *       Loot Drop is not in the strip), so none is skipped; a strip with nothing in it runs neither of the two.
 * </ol>
 * There is no count: the strip is a plain array, not a page.
 */
class OnThisDayQueryCountTest extends OnThisDayApiTest {

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

    /** {@code years} entries of 31 October, each with {@code photos} photos, a voice note and two tags. */
    private void memories(User user, int years, int photos) {
        for (int i = 0; i < years; i++) {
            String id = entryOn(user, (2025 - i) + "-10-31", "Year " + (2025 - i));
            for (int photo = 0; photo < photos; photo++) {
                aPhoto(id, "users/test/otd/" + id + "/" + photo + ".jpg", photo);
            }
            aVoiceNote(id, "users/test/otd/" + id + "/voice.webm", photos);
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
        memories(small, 1, 1);
        User big = aUser();
        memories(big, 15, 3);

        long smallCount = statementsDuring(() -> onThisDay(small).expectStatus().isOk());
        long bigCount = statementsDuring(() -> onThisDay(big).expectStatus().isOk());

        assertThat(bigCount)
                .as("15 entries with 3 photos, a voice note and 2 tags each cost what 1 costs")
                .isEqualTo(smallCount);
        assertThat(smallCount).as("the user, the strip, the media and the tags").isEqualTo(4);
    }

    @Test
    void aFullStripOfTwentyCostsTheSameAndMoreThanTwentyAreCutNotCounted() {
        User full = aUser();
        memories(full, 22, 1);

        long count = statementsDuring(() -> onThisDay(full).expectStatus().isOk());

        assertThat(onThisDayIds(full)).as("22 years of 31 October, 20 shown").hasSize(20);
        assertThat(count)
                .as("more than one Hibernate batch of 50 would not matter either")
                .isEqualTo(4);
    }

    @Test
    void aStripWithNothingInItRunsOnlyTheUserAndTheStripQuery() {
        User sealedOnly = aUser();
        sealUntil(sealedOnly, entryOn(sealedOnly, "2025-10-31", "Not yet"), SEALED_UNTIL);
        User nobodyYet = aUser();

        assertThat(statementsDuring(() -> onThisDay(sealedOnly).expectStatus().isOk()))
                .as("a sealed Loot Drop is not returned, so its media and tags are never read")
                .isEqualTo(2);
        assertThat(statementsDuring(() -> onThisDay(nobodyYet).expectStatus().isOk()))
                .isEqualTo(2);
    }

    @Test
    void aHeaderNamingNobodyRunsOnlyTheUserLookup() {
        User ghost = Fixtures.user();

        assertThat(statementsDuring(() -> onThisDay(ghost).expectStatus().isOk()))
                .as("no user, so no zone and no query for entries")
                .isEqualTo(1);
    }
}
