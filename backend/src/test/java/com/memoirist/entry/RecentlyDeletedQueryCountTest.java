package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.SqlLog;
import com.memoirist.user.User;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * How many SQL statements {@code GET /api/v1/entries/deleted} runs (task 3.11), counted by Hibernate's own
 * {@link Statistics}, and what it loads for a sealed row. It is the other half of the caution that 3.9 left for this
 * task: the media and tags queries behind the timeline start from {@code Entry}, whose {@code @SQLRestriction} hides
 * every entry in Recently deleted, so this list has to fill its rows another way. It lets Hibernate load them lazily,
 * and these tests show what that costs and that a Loot Drop's children are not read with them.
 */
class RecentlyDeletedQueryCountTest extends RecentlyDeletedApiTest {

    private static final String MEDIA_ROLE = "com.memoirist.entry.Entry.media";
    private static final String TAGS_ROLE = "com.memoirist.entry.Entry.tags";

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

    /** A user with this many deleted entries, every {@code sealedEvery}th of them a Loot Drop, each open one dressed. */
    private void populate(User user, int entries, int sealedEvery, int photos) {
        UUID work = newTag(user, "Work");
        UUID family = newTag(user, "Family");
        for (int i = 0; i < entries; i++) {
            String id = entryOn(user, W4, "Entry " + i);
            for (int order = 0; order < photos; order++) {
                aPhoto(id, "users/test/count/" + id + "/" + order + ".jpg", order);
            }
            tag(id, work);
            tag(id, family);
            if (sealedEvery > 0 && i % sealedEvery == 0) {
                sealUntil(user, id, SEALED_UNTIL);
            }
            deleteOk(user, id);
        }
    }

    @Test
    void statisticsAreOnInTheTestProfile() {
        assertThat(statistics().isStatisticsEnabled()).isTrue();
    }

    @Test
    void aPageCostsTheSameNumberOfStatementsWhateverItHolds() {
        User small = aUser();
        populate(small, 1, 0, 1);
        User big = aUser();
        populate(big, 30, 7, 3);

        long smallCount = statementsDuring(
                () -> deletedList(small, "size=50").expectStatus().isOk());
        long bigCount = statementsDuring(
                () -> deletedList(big, "size=50").expectStatus().isOk());

        assertThat(bigCount)
                .as("30 deleted entries (every 7th sealed, 3 photos and 2 tags on each open one) cost what 1 costs")
                .isEqualTo(smallCount);
        assertThat(smallCount)
                .as("the page, the media and the tags (no count: the page has room to spare)")
                .isEqualTo(3);
    }

    @Test
    void theNumberOfPhotosAndTagsPerEntryDoesNotChangeTheCountEither() {
        User bare = aUser();
        populate(bare, 8, 0, 0);
        User heavy = aUser();
        populate(heavy, 8, 0, 9);

        long bareCount =
                statementsDuring(() -> deletedList(bare, "").expectStatus().isOk());
        long heavyCount =
                statementsDuring(() -> deletedList(heavy, "").expectStatus().isOk());

        assertThat(heavyCount).isEqualTo(bareCount);
    }

    @Test
    void aFullPageAddsTheCountAndAShortPageDoesNot() {
        User alice = aUser();
        populate(alice, 5, 0, 1);

        long fullPage = statementsDuring(
                () -> deletedList(alice, "page=0&size=2").expectStatus().isOk());
        long lastPage = statementsDuring(
                () -> deletedList(alice, "page=2&size=2").expectStatus().isOk());

        assertThat(fullPage).as("the page, the count, the media and the tags").isEqualTo(4);
        assertThat(lastPage)
                .as("one entry on a page of two: the total is known without a count")
                .isEqualTo(3);
    }

    @Test
    void aPageOfOnlyLootDropsReadsNeitherMediaNorTags() {
        User alice = aUser();
        populate(alice, 4, 1, 2);

        try (SqlLog sql = SqlLog.capture()) {
            deletedList(alice, "").expectStatus().isOk();
            assertThat(sql.statements()).as("only the page of entries").hasSize(1);
        }
    }

    @Test
    void theChildrenOfALootDropAreNotReadAlongWithTheOpenEntriesOnTheSamePage() {
        User alice = aUser();
        // Entries 0, 3 and 6 are sealed; 1, 2, 4, 5 are open: seven entries, four of them open.
        populate(alice, 7, 3, 2);

        Statistics statistics = statistics();
        statistics.clear();
        deletedList(alice, "size=50").expectStatus().isOk();

        assertThat(statistics.getCollectionStatistics(MEDIA_ROLE).getLoadCount())
                .as("the media lists of the four open entries, and not of the three Loot Drops")
                .isEqualTo(4);
        assertThat(statistics.getCollectionStatistics(TAGS_ROLE).getLoadCount())
                .as("the tags of the four open entries, and not of the three Loot Drops")
                .isEqualTo(4);
    }

    @Test
    void aPageOfFiftyOpenEntriesStillCostsOneMediaAndOneTagsStatement() {
        User alice = aUser();
        populate(alice, 50, 0, 1);

        long statements = statementsDuring(
                () -> deletedList(alice, "size=50").expectStatus().isOk());

        assertThat(statements)
                .as("the largest page: the page, the count (it is full), the media and the tags")
                .isEqualTo(4);
    }

    @Test
    void theRowsOfAPageAreFilledWithTheRightMediaAndTagsWhenSeveralAreLoadedTogether() {
        User alice = aUser();
        UUID work = newTag(alice, "Work");
        String withTwo = entryOn(alice, W4, "Two photos");
        aPhoto(withTwo, "users/test/m/a1.jpg", 0);
        aPhoto(withTwo, "users/test/m/a2.jpg", 1);
        tag(withTwo, work);
        String withNone = entryOn(alice, W4, "No photos");
        String withVoice = entryOn(alice, W4, "A voice note");
        aVoiceNote(withVoice, "users/test/m/v.webm", 0);
        deleteAt("2026-10-31T02:00:00Z", alice, withTwo);
        deleteAt("2026-10-31T03:00:00Z", alice, withNone);
        deleteAt("2026-10-31T04:00:00Z", alice, withVoice);

        String body = deletedBody(alice);

        assertThat(rowIn(body, withTwo)).containsEntry("photoCount", 2).containsEntry("voiceNoteCount", 0);
        assertThat(rowIn(body, withNone)).containsEntry("photoCount", 0).containsEntry("firstMediaKind", null);
        assertThat(rowIn(body, withVoice)).containsEntry("photoCount", 0).containsEntry("voiceNoteCount", 1);
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(rowIn(body, withTwo), "$.tags[*].name"))
                .containsExactly("Work");
        assertThat(com.jayway.jsonpath.JsonPath.<List<?>>read(rowIn(body, withNone), "$.tags"))
                .isEmpty();
    }
}
