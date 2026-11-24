package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * What {@code replyIds} costs (task 3.13), counted with Hibernate's {@link Statistics} like {@code FeedQueryCountTest}.
 * The replies of an entry are a lazy list that {@code EntryMapper} reads only for an entry that has an unlock date, and
 * Hibernate loads the lists of up to 50 entries with one query. So a page of the feed costs:
 * <ul>
 *   <li>nothing extra when it holds no Loot Drop that has opened (an ordinary entry's replies are never asked for, and a
 *       sealed one's neither);
 *   <li>one more statement when it holds opened Loot Drops, however many Loot Drops and replies there are.
 * </ul>
 */
class EntryReplyQueryCountTest extends RecentlyDeletedApiTest {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private long statementsDuring(Runnable request) {
        Statistics statistics = statistics();
        statistics.clear();
        request.run();
        return statistics.getPrepareStatementCount();
    }

    /** {@code drops} Loot Drops that have opened, each with {@code replies} replies. The clock ends after they opened. */
    private List<String> openedDropsWithReplies(User user, int drops, int replies) {
        List<String> ids = new ArrayList<>();
        clockAt("2026-10-31T01:00:00Z"); // before the unlock date, whatever an earlier user's setup did to the clock
        for (int i = 0; i < drops; i++) {
            ids.add(aLootDrop(user, "Drop " + i, OPENS_1));
        }
        clockAt(AFTER_ALL);
        for (String drop : ids) {
            for (int r = 0; r < replies; r++) {
                post(user, json("entryDate", W4, "title", "Reply " + r, "replyToEntryId", drop))
                        .expectStatus()
                        .isCreated();
            }
        }
        return ids;
    }

    private long feedStatements(User user) {
        return statementsDuring(
                () -> feed(user, "page=0&size=10").expectStatus().isOk());
    }

    @Test
    void aFeedPageWithOpenedLootDropsCostsOneStatementMoreWhateverTheyHold() {
        User plain = aUser();
        for (int i = 0; i < 3; i++) {
            entryOn(plain, W4, "Plain " + i);
        }
        User one = aUser();
        openedDropsWithReplies(one, 1, 1);
        User many = aUser();
        openedDropsWithReplies(many, 30, 2);

        long plainCount = feedStatements(plain);
        long oneCount = feedStatements(one);
        long manyCount = feedStatements(many);

        assertThat(oneCount)
                .as("the replies of the opened Loot Drops: one more statement")
                .isEqualTo(plainCount + 1);
        assertThat(manyCount)
                .as("30 Loot Drops and 60 replies cost what 1 and 1 cost")
                .isEqualTo(oneCount);
    }

    @Test
    void theRepliesAreReallyThereInTheBigFeed() {
        User many = aUser();
        List<String> drops = openedDropsWithReplies(many, 3, 2);

        String body = feedBody(many);

        List<List<String>> replyLists = JsonPath.read(body, "$.items[*].entries[?(@.title =~ /Drop.*/)].replyIds");
        assertThat(replyLists).hasSize(3).allSatisfy(ids -> assertThat(ids).hasSize(2));
        assertThat(drops).hasSize(3);
    }

    @Test
    void aFeedPageOfSealedLootDropsAndOrdinaryEntriesAsksForNoReplies() {
        User sealedOnly = aUser();
        aLootDrop(sealedOnly, "Not yet", OPENS_3);
        entryOn(sealedOnly, W4, "Plain");
        User plain = aUser();
        entryOn(plain, W4, "Plain");
        clockAt(OPENS_1);

        assertThat(feedStatements(sealedOnly))
                .as("a sealed Loot Drop's replies are never read, and an ordinary entry has none")
                .isEqualTo(feedStatements(plain));
    }
}
