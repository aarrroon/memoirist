package com.memoirist.music;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import com.memoirist.week.WeekApiTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Soundtracks (D-35) on the three things that can have one: an entry, a week and an Era. For each: set on create or
 * replace, read back, cleared by a full replace that leaves it out, refused when it is not a Spotify URI, and not
 * set at all for someone else's. For entries it adds the privacy rule: while a Loot Drop is sealed its soundtrack
 * is hidden everywhere an entry is shown, and cannot be changed (D-10).
 *
 * <p>The format itself is tried value by value in {@link SoundtrackTest}; here two bad values per endpoint prove the
 * check is wired in and reports {@code fieldErrors.soundtrack}.
 */
class SoundtrackEndpointTest extends WeekApiTest {

    private static final String TRACK = "spotify:track:1a2B3c4D5e6F7g8H9i0J1k";
    private static final String ALBUM = "spotify:album:4aawyAB9vmqN3uQ7FjRGTy";
    private static final String PLAYLIST = "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M";

    private static final String NOT_A_URI =
            "must be a Spotify track, album or playlist URI, such as" + " spotify:track:4uLU6hMCjMI75M1A2tKUQC";

    // Entries

    @Test
    void anEntryIsCreatedWithASoundtrackAndShowsItOnEveryRead() {
        User alice = aUser();

        Map<String, Object> created = createEntry(alice, json("title", "Road trip", "soundtrack", TRACK));

        assertThat(created).containsEntry("soundtrack", TRACK);
        assertThat(row(created.get("id"))).containsEntry("soundtrack", TRACK);
        assertThat(getEntry(alice, created.get("id"))).containsEntry("soundtrack", TRACK);
        String feed = feedBody(alice);
        assertThat(JsonPath.<String>read(feed, "$.items[0].entries[0].soundtrack"))
                .isEqualTo(TRACK);
    }

    @Test
    void anAlbumAndAPlaylistAreAcceptedToo() {
        User alice = aUser();

        assertThat(createEntry(alice, json("title", "A", "soundtrack", ALBUM))).containsEntry("soundtrack", ALBUM);
        assertThat(createEntry(alice, json("title", "B", "soundtrack", PLAYLIST)))
                .containsEntry("soundtrack", PLAYLIST);
    }

    @Test
    void anEntryWithoutOneHasNullAndANullBodyValueIsFine() {
        User alice = aUser();

        assertThat(createEntry(alice, json("title", "Quiet"))).containsEntry("soundtrack", null);
        assertThat(createEntry(alice, json("title", "Quiet again", "soundtrack", null)))
                .containsEntry("soundtrack", null);
    }

    @Test
    void aMalformedUriOnCreateIs400AndNothingIsSaved() {
        User alice = aUser();

        for (String bad :
                new String[] {"", "https://open.spotify.com/track/1a2B3c4D5e6F7g8H9i0J1k", "spotify:track:x"}) {
            String body = bodyText(post(alice, json("title", "Never saved", "soundtrack", bad))
                    .expectStatus()
                    .isBadRequest());

            assertThat(problemType(body)).isEqualTo("validation-failed");
            assertThat(fieldErrors(body)).containsEntry("soundtrack", NOT_A_URI);
            assertThat(body).as("the rejected value is not echoed").doesNotContain("open.spotify.com");
        }
        assertThat(countEntries(alice)).isZero();
    }

    @Test
    void aPutReplacesTheSoundtrackAndRaisesTheVersion() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Mine", "soundtrack", TRACK));

        Map<String, Object> changed = bodyMap(
                put(alice, id, json("version", 0, "entryDate", "2026-10-31", "title", "Mine", "soundtrack", ALBUM))
                        .expectStatus()
                        .isOk());

        assertThat(changed).containsEntry("soundtrack", ALBUM).containsEntry("version", 1);
        assertThat(row(id)).containsEntry("soundtrack", ALBUM).containsEntry("version", 1L);
    }

    @Test
    void aPutThatLeavesTheSoundtrackOutClearsItBecauseAPutIsAFullReplace() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Mine", "soundtrack", TRACK));

        Map<String, Object> changed =
                bodyMap(put(alice, id, json("version", 0, "entryDate", "2026-10-31", "title", "Mine"))
                        .expectStatus()
                        .isOk());

        assertThat(changed).containsEntry("soundtrack", null);
        assertThat(row(id)).containsEntry("soundtrack", null);
    }

    @Test
    void aMalformedUriOnPutIs400AndTheEntryKeepsItsSoundtrack() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Mine", "soundtrack", TRACK));

        String body = bodyText(put(
                        alice,
                        id,
                        json(
                                "version",
                                0,
                                "entryDate",
                                "2026-10-31",
                                "soundtrack",
                                "spotify:artist:1a2B3c4D5e6F7g8H9i0J1k"))
                .expectStatus()
                .isBadRequest());

        assertThat(fieldErrors(body)).containsEntry("soundtrack", NOT_A_URI);
        assertThat(row(id)).containsEntry("soundtrack", TRACK).containsEntry("version", 0L);
    }

    @Test
    void aSealedEntryHidesItsSoundtrackEverywhereAndTheOpenedOneShowsItAgain() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Dear future me", "soundtrack", TRACK));
        String weekId = weekIdOf(id);

        Map<String, Object> sealed =
                bodyMap(seal(alice, id, "2027-01-01T00:00:00Z").expectStatus().isOk());

        assertThat(sealed).containsEntry("isLocked", true).containsEntry("soundtrack", null);
        assertThat(getEntry(alice, id)).containsEntry("soundtrack", null);
        assertThat(JsonPath.<Object>read(feedBody(alice), "$.items[0].entries[0].soundtrack"))
                .isNull();
        assertThat(JsonPath.<Object>read(weekBody(alice, weekId), "$.entries[0].soundtrack"))
                .isNull();
        assertThat(row(id)).as("still stored, only hidden").containsEntry("soundtrack", TRACK);

        clockAt("2027-01-01T00:00:01Z");

        assertThat(getEntry(alice, id)).containsEntry("isLocked", false).containsEntry("soundtrack", TRACK);
    }

    @Test
    void aSealedEntrysSoundtrackCannotBeChangedOrClearedUntilItOpens() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Dear future me", "soundtrack", TRACK));
        seal(alice, id, "2027-01-01T00:00:00Z").expectStatus().isOk();

        String body = bodyText(put(alice, id, json("version", 1, "entryDate", "2026-10-31", "soundtrack", ALBUM))
                .expectStatus()
                .isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("entry-sealed");
        assertThat(row(id)).containsEntry("soundtrack", TRACK);
    }

    @Test
    void aSoundtrackSentToSomeoneElsesEntryIs404AndChangesNothing() {
        User alice = aUser();
        User bob = aUser();
        String id = createEntryId(alice, json("title", "Alice's", "soundtrack", TRACK));

        put(bob, id, json("version", 0, "entryDate", "2026-10-31", "soundtrack", ALBUM))
                .expectStatus()
                .isNotFound();

        assertThat(row(id)).containsEntry("soundtrack", TRACK).containsEntry("version", 0L);
    }

    // Weeks

    @Test
    void aWeekTakesASoundtrackAndAPutThatLeavesItOutClearsIt() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Lab day"));

        Map<String, Object> set = bodyMap(putWeek(alice, weekId, json("title", "Exam week", "soundtrack", PLAYLIST))
                .expectStatus()
                .isOk());

        assertThat(set).containsEntry("soundtrack", PLAYLIST).containsEntry("title", "Exam week");
        assertThat(JsonPath.<String>read(weekBody(alice, weekId), "$.soundtrack"))
                .isEqualTo(PLAYLIST);
        assertThat(JsonPath.<String>read(feedBody(alice), "$.items[0].soundtrack"))
                .isEqualTo(PLAYLIST);

        Map<String, Object> cleared = bodyMap(putWeek(alice, weekId, json("title", "Exam week"))
                .expectStatus()
                .isOk());

        assertThat(cleared).containsEntry("soundtrack", null);
        assertThat(weekSoundtrack(weekId)).isNull();
    }

    @Test
    void aMalformedUriOnAWeekIs400AndTheWeekKeepsItsOld() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Lab day"));
        putWeek(alice, weekId, json("soundtrack", TRACK)).expectStatus().isOk();

        String body =
                bodyText(putWeek(alice, weekId, json("title", "New", "soundtrack", "spotify:track:" + "x".repeat(23)))
                        .expectStatus()
                        .isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsEntry("soundtrack", NOT_A_URI);
        assertThat(weekSoundtrack(weekId)).isEqualTo(TRACK);
    }

    @Test
    void anotherUsersWeekIs404ForASoundtrack() {
        User alice = aUser();
        User bob = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Alice's"));

        putWeek(bob, weekId, json("soundtrack", TRACK)).expectStatus().isNotFound();

        assertThat(weekSoundtrack(weekId)).isNull();
    }

    @Test
    void aWeeksSoundtrackIsNotHiddenWhenItsOnlyEntryIsSealed() {
        // The soundtrack belongs to the week, which is not private; the sealed entry in it is.
        User alice = aUser();
        String entryId = entryOn(alice, W3, "Opens later");
        String weekId = weekIdOf(entryId);
        putWeek(alice, weekId, json("soundtrack", TRACK)).expectStatus().isOk();
        sealUntil(alice, entryId, "2027-01-01T00:00:00Z");

        assertThat(JsonPath.<String>read(weekBody(alice, weekId), "$.soundtrack"))
                .isEqualTo(TRACK);
    }

    // Eras

    @Test
    void anEraIsCreatedWithASoundtrackAndShowsItOnEveryRead() {
        User alice = aUser();

        Map<String, Object> created =
                bodyMap(postEra(alice, json("title", "Uni", "startDate", "2025-02-24", "soundtrack", ALBUM))
                        .expectStatus()
                        .isCreated());

        String eraId = created.get("id").toString();
        assertThat(created).containsEntry("soundtrack", ALBUM);
        assertThat(JsonPath.<String>read(bodyText(getEra(alice, eraId)), "$.soundtrack"))
                .isEqualTo(ALBUM);
        assertThat(JsonPath.<String>read(
                        bodyText(client.get()
                                .uri(ERAS)
                                .header(HEADER, alice.getId().toString())
                                .exchange()),
                        "$[0].soundtrack"))
                .isEqualTo(ALBUM);
    }

    @Test
    void anEraPutReplacesTheSoundtrackAndOneThatLeavesItOutClearsIt() {
        User alice = aUser();
        String eraId = createEra(alice, "Uni", "2025-02-24", null, false);

        Map<String, Object> set =
                bodyMap(putEra(alice, eraId, json("title", "Uni", "startDate", "2025-02-24", "soundtrack", TRACK))
                        .expectStatus()
                        .isOk());
        assertThat(set).containsEntry("soundtrack", TRACK);

        Map<String, Object> cleared = bodyMap(putEra(alice, eraId, json("title", "Uni", "startDate", "2025-02-24"))
                .expectStatus()
                .isOk());
        assertThat(cleared).containsEntry("soundtrack", null);
        assertThat(eraSoundtrack(eraId)).isNull();
    }

    @Test
    void aMalformedUriOnAnEraIs400OnPostAndOnPut() {
        User alice = aUser();
        String eraId = createEra(alice, "Uni", "2025-02-24", null, false);

        String onPost = bodyText(
                postEra(alice, json("title", "X", "startDate", "2025-02-24", "soundtrack", "spotify:track:short"))
                        .expectStatus()
                        .isBadRequest());
        String onPut =
                bodyText(putEra(alice, eraId, json("title", "Uni", "startDate", "2025-02-24", "soundtrack", "nonsense"))
                        .expectStatus()
                        .isBadRequest());

        assertThat(fieldErrors(onPost)).containsEntry("soundtrack", NOT_A_URI);
        assertThat(fieldErrors(onPut)).containsEntry("soundtrack", NOT_A_URI);
    }

    @Test
    void anotherUsersEraIs404ForASoundtrack() {
        User alice = aUser();
        User bob = aUser();
        String eraId = createEra(alice, "Uni", "2025-02-24", null, false);

        putEra(bob, eraId, json("title", "Mine", "startDate", "2025-02-24", "soundtrack", TRACK))
                .expectStatus()
                .isNotFound();
    }

    // Helpers

    private String weekSoundtrack(String weekId) {
        return jdbc.sql("SELECT soundtrack FROM weekly_logs WHERE id = :id")
                .param("id", UUID.fromString(weekId))
                .query((rs, n) -> rs.getString(1))
                .list()
                .getFirst();
    }

    private String eraSoundtrack(String eraId) {
        return jdbc.sql("SELECT soundtrack FROM eras WHERE id = :id")
                .param("id", UUID.fromString(eraId))
                .query((rs, n) -> rs.getString(1))
                .list()
                .getFirst();
    }

    private RestTestClient.ResponseSpec postEra(User user, Map<String, Object> body) {
        return client.post()
                .uri(ERAS)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec getEra(User user, String eraId) {
        return client.get()
                .uri(ERAS + "/" + eraId)
                .header(HEADER, user.getId().toString())
                .exchange();
    }
}
