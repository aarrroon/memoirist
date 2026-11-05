package com.memoirist.tag;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.user.User;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * What {@code entryCount} counts (D-10, D-30): the user's entries that carry the tag, leaving out a sealed Loot Drop
 * until it opens and an entry in Recently deleted until it is restored. Sealed entries are left out by the query itself,
 * so these tests look at the numbers the API gives, never at the entry.
 */
class TagCountTest extends TagApiTest {

    private static final String UNLOCK = "2026-12-25T00:00:00Z";
    private static final String AFTER_UNLOCK = "2026-12-26T00:00:00Z";

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private String taggedEntry(User user, UUID... tagIds) {
        return createEntryId(user, json("title", "An entry", "tagIds", ids(tagIds)));
    }

    @Test
    void anOpenEntryCountsOncePerTagItCarries() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        UUID family = aTag(alice, "Family");
        aTag(alice, "Unused");
        taggedEntry(alice, work, family);
        taggedEntry(alice, work);

        assertThat(countOf(alice, "Work")).isEqualTo(2);
        assertThat(countOf(alice, "Family")).isEqualTo(1);
        assertThat(countOf(alice, "Unused")).isZero();
    }

    @Test
    void aSealedEntryIsNotCountedAndTheTagItAloneCarriesIsStillListedWithZero() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        UUID secret = aTag(alice, "Secret");
        taggedEntry(alice, work);
        String sealed = taggedEntry(alice, work, secret);
        seal(alice, sealed, UNLOCK).expectStatus().isOk();

        assertThat(countOf(alice, "Work"))
                .as("two entries carry it, one is sealed")
                .isEqualTo(1);
        assertThat(countOf(alice, "Secret"))
                .as("only the sealed one carries it")
                .isZero();
        assertThat(tagNames(alice))
                .as("the tag is still the user's own vocabulary")
                .contains("Secret");
        // The answer of a rename and of a duplicate create must not hint at it either.
        assertThat(bodyMap(putTag(alice, secret, "Secret!").expectStatus().isOk()))
                .containsEntry("entryCount", 0);
        assertThat(bodyMap(postTag(alice, "work").expectStatus().isOk())).containsEntry("entryCount", 1);
        assertThat(countLinks(secret))
                .as("the link is there, it is just not counted")
                .isEqualTo(1);
    }

    @Test
    void aLootDropIsCountedFromTheMomentItOpens() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        String drop = taggedEntry(alice, work);
        seal(alice, drop, UNLOCK).expectStatus().isOk();

        clockAt("2026-12-24T23:59:59Z");
        assertThat(countOf(alice, "Work")).as("one second before").isZero();

        clockAt(UNLOCK);
        assertThat(countOf(alice, "Work")).as("at the unlock moment").isEqualTo(1);

        clockAt(AFTER_UNLOCK);
        assertThat(countOf(alice, "Work")).isEqualTo(1);
    }

    @Test
    void aLootDropThatIsSealedAgainStopsCountingUntilItOpensAgain() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        String drop = taggedEntry(alice, work);
        seal(alice, drop, UNLOCK).expectStatus().isOk();
        clockAt(AFTER_UNLOCK);
        assertThat(countOf(alice, "Work")).isEqualTo(1);

        seal(alice, drop, "2027-06-01T00:00:00Z").expectStatus().isOk();

        assertThat(countOf(alice, "Work")).isZero();
    }

    @Test
    void anEntryInRecentlyDeletedIsNotCountedAndCountsAgainWhenItIsBack() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        taggedEntry(alice, work);
        String binned = taggedEntry(alice, work);
        assertThat(countOf(alice, "Work")).isEqualTo(2);

        delete(alice, binned).expectStatus().isNoContent();

        assertThat(countOf(alice, "Work")).isEqualTo(1);
        assertThat(tagNames(alice)).as("deleting an entry never deletes a tag").containsExactly("Work");
        assertThat(linkedTagIds(binned))
                .as("the entry keeps its tag while it waits")
                .containsExactly(work);

        // Restoring clears the mark. This test does it with SQL; RecentlyDeletedVisibilityTest goes through the
        // endpoint.
        jdbc.sql("UPDATE entries SET deleted_at = NULL WHERE id = :id")
                .param("id", UUID.fromString(binned))
                .update();
        assertThat(countOf(alice, "Work")).isEqualTo(2);
    }

    @Test
    void aSealedEntryInRecentlyDeletedIsNotCountedEither() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        String drop = taggedEntry(alice, work);
        seal(alice, drop, UNLOCK).expectStatus().isOk();
        delete(alice, drop).expectStatus().isNoContent();
        clockAt(AFTER_UNLOCK);

        assertThat(countOf(alice, "Work"))
                .as("opened by now, but still in the bin")
                .isZero();
    }

    @Test
    void anEntryCountsOnlyForItsOwnersTags() {
        User alice = aUser();
        User bob = aUser();
        UUID alicesWork = aTag(alice, "Work");
        UUID bobsWork = aTag(bob, "Work");
        taggedEntry(alice, alicesWork);
        taggedEntry(alice, alicesWork);
        taggedEntry(bob, bobsWork);

        assertThat(countOf(alice, "Work")).isEqualTo(2);
        assertThat(countOf(bob, "Work")).isEqualTo(1);
    }

    @Test
    void theCountsInACreateAnAnswerAndAListAgree() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        taggedEntry(alice, work);

        List<Map<String, Object>> list = tagList(alice);
        Map<String, Object> replay =
                bodyMap(postTag(alice, "Work").expectStatus().isOk());
        Map<String, Object> rename =
                bodyMap(putTag(alice, work, "Job").expectStatus().isOk());

        assertThat(list.get(0)).containsEntry("entryCount", 1);
        assertThat(replay).containsEntry("entryCount", 1);
        assertThat(rename).containsEntry("entryCount", 1);
    }

    // How many statements

    @Test
    void theListCostsTheSameNumberOfStatementsForOneTagAndForManyTagsAndEntries() {
        User small = aUser();
        UUID work = aTag(small, "Work");
        taggedEntry(small, work);
        User big = aUser();
        UUID[] tags = new UUID[8];
        for (int i = 0; i < tags.length; i++) {
            tags[i] = aTag(big, "Tag " + i);
        }
        for (int i = 0; i < 12; i++) {
            taggedEntry(big, tags);
        }

        long one = statementsDuring(() -> listTags(small).expectStatus().isOk());
        long many = statementsDuring(() -> listTags(big).expectStatus().isOk());

        assertThat(many).as("a count query per tag would grow with the tags").isEqualTo(one);
        assertThat(one).as("the counts, and the tags").isEqualTo(2);
    }

    private long statementsDuring(Runnable request) {
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        request.run();
        return statistics.getPrepareStatementCount();
    }
}
