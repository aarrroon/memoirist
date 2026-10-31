package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Where an entry in Recently deleted can be seen (task 3.11, D-30): nowhere, and again everywhere once it is restored.
 * Each place has its own tests since task 3.3 to 3.10; this is the one test that looks at all of them together, so a
 * place that forgot the rule, or a restore that forgot to undo it, is caught here.
 *
 * <p>One journal, built once: a week with three entries (one deleted later, one that stays, one opened Loot Drop that
 * nobody has seen), a week with a single entry (so deleting it empties the week), a tag on two of the entries, and an
 * Era over each week. The clock is moved past the Loot Drop's unlock date before anything is deleted.
 */
class RecentlyDeletedVisibilityTest extends RecentlyDeletedApiTest {

    /** Everything a user can see of their journal, collected from every place an entry shows up. */
    private record Seen(
            List<String> feed,
            List<String> feedWeeks,
            List<String> busyWeek,
            List<String> quietWeek,
            List<String> timeline,
            int workCount,
            List<String> workFilter,
            List<String> openedStrip,
            int busyEraCount,
            int quietEraCount) {}

    private User alice;
    private String target; // the entry that gets deleted, restored or erased
    private String stays; // an ordinary entry that is never touched
    private String loneEntry; // the only entry in its week
    private String loot; // a Loot Drop that has opened and has not been marked seen
    private String busyWeek;
    private String quietWeek;
    private UUID work;
    private String busyEra;
    private String quietEra;

    private void aJournal() {
        alice = aUser();
        target = entryOn(alice, "2026-10-27", "The one to delete");
        stays = entryOn(alice, "2026-10-28", "Stays");
        loneEntry = entryOn(alice, "2026-10-21", "Alone in its week");
        loot = aLootDrop(alice, "Opened and unseen", OPENS_1);
        work = newTag(alice, "Work");
        tag(target, work);
        tag(stays, work);
        busyWeek = weekIdOf(target);
        quietWeek = weekIdOf(loneEntry);
        busyEra = createEra(alice, "Busy", "2026-10-26", "2026-11-01", false);
        quietEra = createEra(alice, "Quiet", "2026-10-19", "2026-10-25", false);
        clockAt(OPENS_1);
    }

    private Seen seen() {
        String feed = feedBody(alice);
        return new Seen(
                JsonPath.read(feed, "$.items[*].entries[*].id"),
                weekStarts(feed),
                entriesOfWeek(busyWeek),
                entriesOfWeek(quietWeek),
                ids(listBody(alice)),
                workCount(),
                ids(listBody(alice, "tagId=" + work)),
                openedIds(alice),
                eraCountOfWeek(busyEra, busyWeek),
                eraCountOfWeek(quietEra, quietWeek));
    }

    private List<String> entriesOfWeek(String weekId) {
        return JsonPath.read(weekBody(alice, weekId), "$.entries[*].id");
    }

    private int workCount() {
        RestTestClient.ResponseSpec response = client.get()
                .uri("/api/v1/tags")
                .header(HEADER, alice.getId().toString())
                .exchange();
        response.expectStatus().isOk();
        List<Integer> counts = JsonPath.read(bodyText(response), "$[?(@.name == 'Work')].entryCount");
        return counts.getFirst();
    }

    private int eraCountOfWeek(String eraId, String weekId) {
        RestTestClient.ResponseSpec response = client.get()
                .uri(ERAS + "/" + eraId)
                .header(HEADER, alice.getId().toString())
                .exchange();
        response.expectStatus().isOk();
        List<Integer> counts =
                JsonPath.read(bodyText(response), "$.weeklyLogs[?(@.id == '" + weekId + "')].entryCount");
        return counts.getFirst();
    }

    private void deleteTheThree() {
        deleteOk(alice, target);
        deleteOk(alice, loneEntry);
        deleteOk(alice, loot);
    }

    @Test
    void beforeAnythingIsDeletedEveryPlaceShowsTheEntries() {
        aJournal();

        Seen seen = seen();

        assertThat(seen.feed()).contains(target, stays, loneEntry, loot);
        assertThat(seen.feedWeeks()).hasSize(2);
        assertThat(seen.busyWeek()).contains(target, stays, loot);
        assertThat(seen.quietWeek()).containsExactly(loneEntry);
        assertThat(seen.timeline()).contains(target, stays, loneEntry, loot);
        assertThat(seen.workCount()).isEqualTo(2);
        assertThat(seen.workFilter()).containsExactlyInAnyOrder(target, stays);
        assertThat(seen.openedStrip()).containsExactly(loot);
        assertThat(seen.busyEraCount()).isEqualTo(3);
        assertThat(seen.quietEraCount()).isEqualTo(1);
    }

    @Test
    void aDeletedEntryIsInNoneOfThem() {
        aJournal();

        deleteTheThree();
        Seen seen = seen();

        assertThat(seen.feed()).as("the feed").containsExactly(stays);
        assertThat(seen.feedWeeks())
                .as("a week whose only entry is deleted is hidden")
                .hasSize(1);
        assertThat(seen.busyWeek()).as("its week").containsExactly(stays);
        assertThat(seen.quietWeek())
                .as("a week with nothing left still opens, empty")
                .isEmpty();
        assertThat(seen.timeline()).as("the timeline").containsExactly(stays);
        assertThat(seen.workCount()).as("the tag's count").isEqualTo(1);
        assertThat(seen.workFilter()).as("the tag filter").containsExactly(stays);
        assertThat(seen.openedStrip()).as("the Opened strip").isEmpty();
        assertThat(seen.busyEraCount()).as("the Era over the busy week").isEqualTo(1);
        assertThat(seen.quietEraCount()).as("the Era over the quiet week").isZero();
        assertThat(deletedIds(alice)).containsExactlyInAnyOrder(target, loneEntry, loot);
    }

    @Test
    void restoredEntriesAreBackInAllOfThemExactlyAsBefore() {
        aJournal();
        Seen before = seen();
        deleteTheThree();

        restore(alice, target).expectStatus().isOk();
        restore(alice, loneEntry).expectStatus().isOk();
        restore(alice, loot).expectStatus().isOk();

        assertThat(seen()).isEqualTo(before);
        assertThat(deletedIds(alice)).isEmpty();
    }

    @Test
    void erasedEntriesNeverComeBackAnywhere() {
        aJournal();
        deleteTheThree();

        erase(alice, target).expectStatus().isNoContent();
        erase(alice, loneEntry).expectStatus().isNoContent();
        erase(alice, loot).expectStatus().isNoContent();
        Seen seen = seen();

        assertThat(seen.feed()).containsExactly(stays);
        assertThat(seen.timeline()).containsExactly(stays);
        assertThat(seen.workCount()).isEqualTo(1);
        assertThat(seen.openedStrip()).isEmpty();
        assertThat(seen.busyEraCount()).isEqualTo(1);
        assertThat(deletedIds(alice)).isEmpty();
        restore(alice, target).expectStatus().isNotFound();
    }
}
