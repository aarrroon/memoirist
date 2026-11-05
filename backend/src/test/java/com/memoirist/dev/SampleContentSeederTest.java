package com.memoirist.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.Fixtures;
import com.memoirist.entry.EntryRepository;
import com.memoirist.era.EraRepository;
import com.memoirist.media.MediaApiTest;
import com.memoirist.media.MediaRepository;
import com.memoirist.storage.BucketBootstrap;
import com.memoirist.storage.StorageProperties;
import com.memoirist.tag.TagRepository;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeekService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The sample content seeder (task 3.16) against real Postgres and real SeaweedFS, read back through the API as the UI
 * will read it. The seeder is not a bean in the {@code test} profile ({@code SampleContentBeanTest}), so the test builds
 * one from the beans the app has. It writes for the dev user, whom other tests also use, so every test starts and ends
 * with that user's journal empty.
 *
 * <p>The clock starts on Saturday 2026-10-31 (Melbourne), so this week's Monday is 2026-10-26.
 */
class SampleContentSeederTest extends MediaApiTest {

    private static final String DEV = Fixtures.DEV_USER_ID.toString();
    private static final String DRAFT = "A draft I didn't keep";

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private EntryRepository entryRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private EraRepository eraRepository;

    @Autowired
    private WeekService weekService;

    @Autowired
    private BucketBootstrap bucketBootstrap;

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private Clock appClock;

    @Autowired
    private PlatformTransactionManager transactions;

    private SampleContentSeeder seeder;

    @BeforeEach
    void startWithAnEmptyJournal() {
        emptyTheDevUsersJournal();
        seeder = seederFor(users);
    }

    @AfterEach
    void leaveTheJournalEmptyForTheOtherTests() {
        emptyTheDevUsersJournal();
    }

    private SampleContentSeeder seederFor(UserRepository userRepository) {
        return new SampleContentSeeder(
                userRepository,
                tagRepository,
                templates,
                entryRepository,
                mediaRepository,
                eraRepository,
                weekService,
                storage,
                bucketBootstrap,
                jdbc,
                appClock,
                transactions);
    }

    /** The dev user stays (the other tests rely on it); what the seeder made goes. Media rows go with their entries. */
    private void emptyTheDevUsersJournal() {
        for (String table : List.of("eras", "entries", "weekly_logs", "tags", "templates")) {
            jdbc.sql("DELETE FROM " + table + " WHERE user_id = :id")
                    .param("id", Fixtures.DEV_USER_ID)
                    .update();
        }
    }

    // Reading it back

    private String body(String uri) {
        RestTestClient.ResponseSpec response =
                client.get().uri(uri).header(HEADER, DEV).exchange();
        response.expectStatus().isOk();
        return bodyText(response);
    }

    private String feed() {
        return body("/api/v1/feed?size=50");
    }

    /** Every entry of every week on the feed. */
    private List<Map<String, Object>> feedEntries(String feed) {
        return JsonPath.read(feed, "$.items[*].entries[*]");
    }

    private Map<String, Object> feedEntryTitled(String feed, String title) {
        List<Map<String, Object>> found = feedEntries(feed).stream()
                .filter(entry -> title.equals(entry.get("title")))
                .toList();
        assertThat(found).as("entries titled %s", title).hasSize(1);
        return found.getFirst();
    }

    private long count(String sql) {
        return jdbc.sql(sql).param("id", Fixtures.DEV_USER_ID).query(Long.class).single();
    }

    // Done when: GET /feed returns the sample content

    @Test
    void anEmptyJournalIsFilledAndTheFeedShowsIt() {
        assertThat(body("/api/v1/feed")).contains("\"items\":[]");

        assertThat(seeder.seed()).isTrue();

        String feed = feed();
        // Six weeks hold entries (the week of the Recently deleted draft has other entries too).
        assertThat((Integer) JsonPath.read(feed, "$.totalItems")).isEqualTo(6);
        List<String> titles = JsonPath.read(feed, "$.items[*].entries[*].title");
        assertThat(titles)
                .contains(
                        "Day one in Hobart",
                        "Cradle Mountain",
                        "First team lunch",
                        "Sunday reset",
                        "Quiet day",
                        "Dear past me",
                        "Letter to future me",
                        "From my paper diary")
                .doesNotContain(DRAFT);
        List<String> weeks = JsonPath.read(feed, "$.items[*].startDate");
        assertThat(weeks).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(weeks.getFirst()).isEqualTo("2026-10-26");
    }

    @Test
    void theSampleHasTheKindsOfEntryTheUiNeedsToShow() {
        seeder.seed();
        String feed = feed();

        // Started from the template, with the template's headings in its caption and the empty prompt left out.
        String templateId = JsonPath.read(body("/api/v1/templates"), "$[0].id");
        Map<String, Object> dayOne = feedEntryTitled(feed, "Day one in Hobart");
        assertThat(dayOne.get("templateId")).isEqualTo(templateId);
        assertThat((String) dayOne.get("contentText")).contains("Where did we go?", "Best moment", "Food");
        assertThat((String) dayOne.get("contentText")).doesNotContain("Tomorrow");

        // A backdated diary page: marked as a page, months before the other entries.
        Map<String, Object> page = feedEntryTitled(feed, "From my paper diary");
        assertThat(page.get("entryDate")).isEqualTo("2026-07-24");
        assertThat(JsonPath.<List<String>>read(page, "$.media[*].purpose")).containsExactly("PAGE_SCAN");

        // Photo only: no title, no caption, two photos in order.
        List<Map<String, Object>> photoOnly = feedEntries(feed).stream()
                .filter(entry -> entry.get("title") == null && !(Boolean) entry.get("isLocked"))
                .toList();
        assertThat(photoOnly).hasSize(1);
        assertThat(photoOnly.getFirst().get("contentText")).isEqualTo("");
        assertThat(JsonPath.<List<Integer>>read(photoOnly.getFirst(), "$.media[*].displayOrder"))
                .containsExactly(0, 1);

        // An entry with a soundtrack.
        assertThat(feedEntryTitled(feed, "Cradle Mountain").get("soundtrack"))
                .isEqualTo(SampleContentSeeder.SOUNDTRACK);

        // A sealed Loot Drop: a locked row with nothing private in it, and no picture signed.
        List<Map<String, Object>> locked = feedEntries(feed).stream()
                .filter(entry -> (Boolean) entry.get("isLocked"))
                .toList();
        assertThat(locked).hasSize(1);
        assertThat(locked.getFirst().get("title")).isNull();
        assertThat(locked.getFirst().get("contentJson")).isNull();
        assertThat((List<?>) locked.getFirst().get("media")).isEmpty();
        assertThat(feed).doesNotContain("For the end of the year").doesNotContain("project shipped");
    }

    @Test
    void anOpenedLootDropIsInTheOpenedStripAndHasItsReply() {
        seeder.seed();

        assertThat(JsonPath.<List<String>>read(body("/api/v1/entries/opened"), "$[*].title"))
                .containsExactly("Letter to future me");

        String feed = feed();
        Map<String, Object> letter = feedEntryTitled(feed, "Letter to future me");
        Map<String, Object> reply = feedEntryTitled(feed, "Dear past me");
        assertThat(reply.get("replyToEntryId")).isEqualTo(letter.get("id"));
        assertThat((List<Object>) letter.get("replyIds")).containsExactly(reply.get("id"));
    }

    @Test
    void theDeletedEntryIsInRecentlyDeletedAndNowhereElse() {
        seeder.seed();

        assertThat(JsonPath.<List<String>>read(body("/api/v1/entries/deleted"), "$.items[*].title"))
                .containsExactly(DRAFT);
        assertThat(JsonPath.<List<String>>read(body("/api/v1/entries?size=100"), "$.items[*].title"))
                .doesNotContain(DRAFT);
    }

    @Test
    void tagsAndTheTemplateExistAndCountOnlyOpenEntries() {
        seeder.seed();

        String tags = body("/api/v1/tags");
        assertThat(JsonPath.<List<String>>read(tags, "$[*].name"))
                .containsExactlyInAnyOrder("Family", "Gratitude", "Trip", "Work");
        assertThat(JsonPath.<List<Integer>>read(tags, "$[?(@.name == 'Trip')].entryCount"))
                .containsExactly(2);
        assertThat(JsonPath.<List<String>>read(body("/api/v1/templates"), "$[*].name"))
                .containsExactly("Trip day");
    }

    // Eras

    @Test
    void theErasOverlapTheWayTheFeedRulesNeedAndOneHasACover() throws IOException, InterruptedException {
        seeder.seed();

        String eras = body("/api/v1/eras");
        assertThat(JsonPath.<List<String>>read(eras, "$[*].title")).containsExactly("New job", "Tasmania", "Uni Days");

        // Tasmania is wholly inside Uni Days; New job starts inside Uni Days and runs on (a partial overlap, D-22).
        assertThat(JsonPath.<List<String>>read(eras, "$[?(@.title == 'Uni Days')].endDate"))
                .containsExactly("2026-10-21");
        assertThat(JsonPath.<List<String>>read(eras, "$[?(@.title == 'Tasmania')].startDate"))
                .containsExactly("2026-10-06");
        assertThat(JsonPath.<List<String>>read(eras, "$[?(@.title == 'Tasmania')].endDate"))
                .containsExactly("2026-10-10");
        assertThat(JsonPath.<List<String>>read(eras, "$[?(@.title == 'New job')].startDate"))
                .containsExactly("2026-10-12");
        assertThat(JsonPath.<List<Object>>read(eras, "$[?(@.title == 'New job')].endDate"))
                .containsExactly((Object) null);

        // Exactly one cover, Tasmania's, and its URL serves the PNG the seeder stored.
        assertThat(JsonPath.<List<String>>read(eras, "$[?(@.cover != null)].title"))
                .containsExactly("Tasmania");
        List<String> urls = JsonPath.read(eras, "$[?(@.title == 'Tasmania')].cover.fileUrl");
        HttpResponse<byte[]> picture = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(urls.getFirst())).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        assertThat(picture.statusCode()).isEqualTo(200);
        assertThat(picture.headers().firstValue("Content-Type")).hasValue("image/png");
        assertThat(picture.body()).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');

        // The feed: the trip week is in Tasmania and Uni Days (the nesting is the client's job), the two partly
        // overlapping Eras share the week before last, and this week is in New job only.
        String feed = feed();
        assertThat(JsonPath.<List<String>>read(feed, "$.items[?(@.startDate == '2026-10-05')].eras[*].title"))
                .containsExactlyInAnyOrder("Tasmania", "Uni Days");
        assertThat(JsonPath.<List<String>>read(feed, "$.items[?(@.startDate == '2026-10-12')].eras[*].title"))
                .containsExactlyInAnyOrder("New job", "Uni Days");
        assertThat(JsonPath.<List<String>>read(feed, "$.items[?(@.startDate == '2026-10-26')].eras[*].title"))
                .containsExactly("New job");
    }

    @Test
    void everyPictureIsReallyInStorageUnderItsKey() {
        seeder.seed();

        List<String> keys = jdbc.sql("SELECT m.object_key FROM media m JOIN entries e ON e.id = m.entry_id"
                        + " WHERE e.user_id = :id ORDER BY m.object_key")
                .param("id", Fixtures.DEV_USER_ID)
                .query(String.class)
                .list();
        assertThat(keys).hasSize(6); // Hobart, Cradle Mountain, the diary page, two photo-only, the sealed letter's
        for (String key : keys) {
            assertThat(key)
                    .startsWith("users/" + Fixtures.DEV_USER_ID + "/entries/")
                    .endsWith(".png");
            assertThat(s3.headObject(request ->
                                    request.bucket(storageProperties.bucket()).key(key))
                            .contentType())
                    .isEqualTo("image/png");
        }
    }

    // Done when: running it twice changes nothing

    // TODO(human) PRACTICE: Write the "run it twice" test. Take a snapshot of the dev user's journal after the first
    //   run (the ids of every table the seeder writes, and each entry's version and updated_at), run the seeder again,
    //   and prove the second run returns false and the snapshot is identical. Decide what to compare: counts alone
    //   would miss a run that deleted one row and added another.
    // Hint: ids made from fixed names (SampleContentSeeder.id) are what make an exact comparison possible; a second
    //   run that inserted copies would change the ids as well as the counts. Compare sorted lists, not sizes.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    @Test
    void runningItTwiceChangesNothing() {
        assertThat(seeder.seed()).isTrue();
        Snapshot first = snapshot();

        assertThat(seeder.seed()).isFalse();

        assertThat(snapshot()).isEqualTo(first);
        assertThat(first.entries()).hasSize(SampleContentSeeder.ENTRY_COUNT);
    }
    // └── END ANSWER ▲ ──

    /** Every row the seeder writes, in a fixed order; an entry also by its version and last write. */
    private record Snapshot(
            List<String> entries,
            List<UUID> media,
            List<UUID> eras,
            List<UUID> tags,
            List<UUID> templates,
            List<UUID> weeks) {}

    private Snapshot snapshot() {
        return new Snapshot(
                jdbc.sql(
                                "SELECT id || ':' || version || ':' || updated_at FROM entries WHERE user_id = :id ORDER BY id")
                        .param("id", Fixtures.DEV_USER_ID)
                        .query(String.class)
                        .list(),
                idsOf(
                        "SELECT m.id FROM media m JOIN entries e ON e.id = m.entry_id WHERE e.user_id = :id ORDER BY m.id"),
                idsOf("SELECT id FROM eras WHERE user_id = :id ORDER BY id"),
                idsOf("SELECT id FROM tags WHERE user_id = :id ORDER BY id"),
                idsOf("SELECT id FROM templates WHERE user_id = :id ORDER BY id"),
                idsOf("SELECT id FROM weekly_logs WHERE user_id = :id ORDER BY id"));
    }

    private List<UUID> idsOf(String sql) {
        return jdbc.sql(sql).param("id", Fixtures.DEV_USER_ID).query(UUID.class).list();
    }

    @Test
    void aJournalThatHasBeenUsedIsLeftAlone_whateverKindOfRowIsInIt() {
        // Each of the four kinds of row alone is enough to skip. An entry in Recently deleted is the sneaky one,
        // because JPA hides it and only SQL sees it.
        UUID week = UUID.randomUUID();
        jdbc.sql("INSERT INTO weekly_logs (id, user_id, start_date, end_date)"
                        + " VALUES (:w, :u, DATE '2026-10-26', DATE '2026-11-01')")
                .param("w", week)
                .param("u", Fixtures.DEV_USER_ID)
                .update();
        jdbc.sql("INSERT INTO entries (id, user_id, weekly_log_id, entry_date, deleted_at)"
                        + " VALUES (:id, :u, :w, DATE '2026-10-26', NOW())")
                .param("id", UUID.randomUUID())
                .param("w", week)
                .param("u", Fixtures.DEV_USER_ID)
                .update();
        assertThat(seeder.seed()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM entries WHERE user_id = :id")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM eras WHERE user_id = :id")).isZero();

        emptyTheDevUsersJournal();
        jdbc.sql("INSERT INTO tags (id, user_id, name) VALUES (:id, :u, 'Mine')")
                .param("id", UUID.randomUUID())
                .param("u", Fixtures.DEV_USER_ID)
                .update();
        assertThat(seeder.seed()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM entries WHERE user_id = :id")).isZero();

        emptyTheDevUsersJournal();
        aTemplate(users.findById(Fixtures.DEV_USER_ID).orElseThrow(), "Mine");
        assertThat(seeder.seed()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM tags WHERE user_id = :id")).isZero();

        emptyTheDevUsersJournal();
        jdbc.sql("INSERT INTO eras (id, user_id, title, start_date) VALUES (:id, :u, 'Mine', DATE '2026-01-01')")
                .param("id", UUID.randomUUID())
                .param("u", Fixtures.DEV_USER_ID)
                .update();
        assertThat(seeder.seed()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM templates WHERE user_id = :id")).isZero();
    }

    @Test
    void anotherUsersContentDoesNotStopTheSeeder() {
        var alice = aUser();
        jdbc.sql("INSERT INTO tags (id, user_id, name) VALUES (:id, :u, 'Hers')")
                .param("id", UUID.randomUUID())
                .param("u", alice.getId())
                .update();

        assertThat(seeder.seed()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM tags WHERE user_id = :id")).isEqualTo(4);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM tags WHERE user_id = :id")
                        .param("id", alice.getId())
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void aFailureWhileStoringThePicturesLeavesNoRowsAndTheNextRunSucceeds() {
        doThrow(new IllegalStateException("simulated failure"))
                .when(storage)
                .put(endsWith("/" + SampleContentSeeder.id("media:sealed") + ".png"), any(byte[].class), anyString());

        assertThatIllegalStateException().isThrownBy(seeder::seed).withMessage("simulated failure");
        assertThat(count("SELECT COUNT(*) FROM entries WHERE user_id = :id")).isZero();
        assertThat(count("SELECT COUNT(*) FROM weekly_logs WHERE user_id = :id"))
                .isZero();

        reset(storage);
        assertThat(seeder.seed()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM entries WHERE user_id = :id"))
                .isEqualTo(SampleContentSeeder.ENTRY_COUNT);
    }

    @Test
    void withoutTheDevUserItSaysWhatToCheck() {
        // A mock's Optional answers are empty, so this repository knows nobody.
        SampleContentSeeder noUser = seederFor(mock(UserRepository.class));

        assertThatIllegalStateException().isThrownBy(noUser::seed).withMessageContaining("db/seed-local");
    }
}
