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
 * How many SQL statements {@code GET /api/v1/entries} runs, counted by Hibernate's own {@link Statistics}: a page must
 * cost the same however many entries, photos and tags it holds. It is the guard against the classic N+1 (one query for
 * the page, then one more for every row), which no functional test notices because the answer is the same, only slower.
 * It is the same technique as {@code FeedQueryCountTest}; see there for how the counters work.
 *
 * <p>What a page of the timeline costs, and why (see {@code EntryListService}):
 * <ol>
 *   <li>the page of entries;
 *   <li>the media of the open entries, and 3. their tags: one query each with a {@code left join fetch}, because
 *       {@code EntryMapper} reads the lazy lists of an open entry. A sealed entry's are never read, so a page of nothing
 *       but Loot Drops runs neither;
 *   <li>the count for {@code totalItems}, but only when the page is full: Spring Data skips it for a page with room to
 *       spare, since the total is then the offset plus the rows it got. The tests below compare pages of the same
 *       fullness;
 *   <li>with a {@code tagId}, one more at the start to check that the tag is the user's.
 * </ol>
 */
class EntryListQueryCountTest extends EntryListApiTest {

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

    @Test
    void statisticsAreOnInTheTestProfile() {
        assertThat(statistics().isStatisticsEnabled()).isTrue();
    }

    @Test
    void aPageCostsTheSameNumberOfStatementsWhateverItHolds() {
        User small = aUser();
        String lonely = entryOn(small, W4, "One entry");
        aPhoto(lonely, "users/test/small/1.jpg", 0);
        User big = aUser();
        populate(big, 40, 3);

        long smallCount =
                statementsDuring(() -> list(small, "size=50").expectStatus().isOk());
        long bigCount =
                statementsDuring(() -> list(big, "size=50").expectStatus().isOk());

        assertThat(bigCount)
                .as(
                        "40 entries (every 7th sealed, 3 photos, a voice note and 2 tags on each open one) cost what 1 costs")
                .isEqualTo(smallCount);
        assertThat(smallCount)
                .as("the page, the media and the tags (no count: the page has room to spare)")
                .isEqualTo(3);
    }

    @Test
    void thePageCostsTheSameJustPastTheBatchSizeOf50OpenEntriesAndAtTheLargestPage() {
        User fifty = aUser();
        populate(fifty, 50, 1);
        User hundred = aUser();
        populate(hundred, 99, 1);

        long fiftyCount =
                statementsDuring(() -> list(fifty, "size=100").expectStatus().isOk());
        long hundredCount =
                statementsDuring(() -> list(hundred, "size=100").expectStatus().isOk());

        assertThat(fiftyCount).as("43 open entries").isEqualTo(3);
        assertThat(hundredCount)
                .as("99 entries, 85 of them open (a page of 100 with room to spare, so no count): more than one"
                        + " Hibernate batch of 50 would have been")
                .isEqualTo(fiftyCount);
    }

    @Test
    void theNumberOfPhotosAndTagsPerEntryDoesNotChangeTheCountEither() {
        User bare = aUser();
        populate(bare, 10, 0);
        User heavy = aUser();
        populate(heavy, 10, 8);

        long bareCount = statementsDuring(() -> list(bare, "").expectStatus().isOk());
        long heavyCount = statementsDuring(() -> list(heavy, "").expectStatus().isOk());

        assertThat(heavyCount)
                .as("8 photos, a voice note and 2 tags on every open entry")
                .isEqualTo(bareCount);
    }

    @Test
    void aTagFilterAddsExactlyOneStatementForTheTagCheckAndStaysFlat() {
        User small = aUser();
        UUID smallTag = newTag(small, "Work");
        tag(entryOn(small, W4, "One"), smallTag);
        User big = aUser();
        UUID bigTag = newTag(big, "Work");
        populate(big, 60, 2, bigTag);

        long smallCount = statementsDuring(
                () -> list(small, "tagId=" + smallTag).expectStatus().isOk());
        long bigCount = statementsDuring(
                () -> list(big, "tagId=" + bigTag + "&size=100").expectStatus().isOk());

        assertThat(smallCount)
                .as("the tag check, the page, the media and the tags")
                .isEqualTo(4);
        assertThat(bigCount).as("60 tagged entries, some of them sealed").isEqualTo(smallCount);
    }

    @Test
    void aFullPageAddsTheCountAndAShortPageDoesNot() {
        User alice = aUser();
        populate(alice, 5, 1);

        long firstOfThree = statementsDuring(
                () -> list(alice, "page=0&size=2").expectStatus().isOk());
        long lastOfThree = statementsDuring(
                () -> list(alice, "page=2&size=2").expectStatus().isOk());
        long roomToSpare = statementsDuring(
                () -> list(alice, "page=0&size=10").expectStatus().isOk());

        assertThat(firstOfThree)
                .as("page, count, media, tags: a full first page has to ask how many there are")
                .isEqualTo(4);
        assertThat(lastOfThree)
                .as("the last page is short, so its total is the offset plus its rows")
                .isEqualTo(3);
        assertThat(roomToSpare).as("no count when the page has room to spare").isEqualTo(3);
    }

    @Test
    void aPageOfOnlySealedLootDropsNeverLoadsMediaOrTags() {
        User alice = aUser();
        for (int i = 0; i < 3; i++) {
            String drop = entryOn(alice, W4, "Secret " + i);
            aPhoto(drop, "users/test/sealed/" + i + ".jpg", 0);
            tag(drop, newTag(alice, "secret " + i));
            sealUntil(alice, drop, SEALED_UNTIL);
        }

        try (SqlLog sql = SqlLog.capture()) {
            long count = statementsDuring(() -> list(alice, "").expectStatus().isOk());

            assertThat(count)
                    .as("only the page: the mapper skips a sealed entry's collections")
                    .isEqualTo(1);
            assertThat(sql.statements()).noneMatch(statement -> statement.matches(".*\\b(from|join) media\\b.*"));
            assertThat(sql.statements()).noneMatch(statement -> statement.contains("entry_tags"));
        }
    }

    @Test
    void anEmptyJournalCostsOnlyTheOneQueryForThePage() {
        User alice = aUser();

        long count = statementsDuring(() -> list(alice, "").expectStatus().isOk());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void theMediaAndTagsAreEachOneQueryThatLeavesSealedEntriesOut() {
        User alice = aUser();
        populate(alice, 12, 2);
        String drop = entryOn(alice, W4, "A drop with a photo");
        aPhoto(drop, "users/test/entries/drop/sealed-photo.jpg", 0);
        tag(drop, newTag(alice, "secret"));
        sealUntil(alice, drop, SEALED_UNTIL);

        try (SqlLog sql = SqlLog.capture()) {
            list(alice, "").expectStatus().isOk();

            List<String> media = sql.statements().stream()
                    .filter(statement -> statement.matches(".*\\bjoin media\\b.*"))
                    .toList();
            List<String> tags = sql.statements().stream()
                    .filter(statement -> statement.contains("entry_tags"))
                    .toList();
            assertThat(media).as("one query for all the open entries' media").hasSize(1);
            assertThat(media.getFirst())
                    .as("sealed entries are left out in the query")
                    .contains("unlock_date");
            assertThat(tags).as("one query for all the open entries' tags").hasSize(1);
            assertThat(tags.getFirst())
                    .as("sealed entries are left out in the query")
                    .contains("unlock_date");
            assertThat(sql.statements())
                    .as("nothing is left for Hibernate's lazy batches")
                    .noneMatch(statement -> statement.contains("= any (?)"));
        }
    }

    @Test
    void theTagFilterLeavesSealedEntriesOutInBothTheListAndTheCountQuery() {
        User alice = aUser();
        UUID work = newTag(alice, "Work");
        populate(alice, 8, 1, work);

        try (SqlLog sql = SqlLog.capture()) {
            list(alice, "tagId=" + work + "&size=2").expectStatus().isOk();

            // The page query is the one with an order by; the count query is the one that counts. The fourth
            // statement, the tags of the open entries, also joins entry_tags and is not one of these two.
            List<String> onEntries = sql.statements().stream()
                    .filter(statement -> statement.contains("join entry_tags"))
                    .filter(statement -> statement.contains("order by") || statement.startsWith("select count("))
                    .toList();
            assertThat(onEntries)
                    .as("the page query and the count query")
                    .hasSize(2)
                    .allSatisfy(statement -> assertThat(statement)
                            .as("the seal is in the SQL itself, not applied afterwards")
                            .contains("unlock_date"));
        }
    }

    @Test
    void aListOnlyReads() {
        User alice = aUser();
        populate(alice, 6, 2);

        try (SqlLog sql = SqlLog.capture()) {
            list(alice, "").expectStatus().isOk();

            assertThat(sql.statements()).isNotEmpty().allMatch(statement -> statement.startsWith("select"));
        }
    }

    // The journals

    private void populate(User user, int entries, int photos) {
        populate(user, entries, photos, null);
    }

    /**
     * {@code entries} entries on the same Monday, each with {@code photos} photos, and, if there are photos, a voice
     * note and two tags. Every seventh is a sealed Loot Drop. When {@code tagged} is given, every entry carries it.
     */
    private void populate(User user, int entries, int photos, UUID tagged) {
        UUID extra = newTag(user, "Extra " + UUID.randomUUID());
        for (int i = 0; i < entries; i++) {
            String id = entryOn(user, W4, "Entry " + i);
            for (int p = 0; p < photos; p++) {
                aPhoto(id, "users/test/" + id + "/" + p + ".jpg", p);
            }
            if (photos > 0) {
                aVoiceNote(id, "users/test/" + id + "/voice.webm", photos);
                tag(id, extra);
            }
            if (tagged != null) {
                tag(id, tagged);
            }
            if (i % 7 == 6) {
                sealUntil(user, id, SEALED_UNTIL);
            }
        }
    }
}
