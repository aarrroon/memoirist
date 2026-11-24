package com.memoirist.dev;

import com.memoirist.entry.ContentTextExtractor;
import com.memoirist.entry.Entry;
import com.memoirist.entry.EntryContentValidator;
import com.memoirist.entry.EntryRepository;
import com.memoirist.era.Era;
import com.memoirist.era.EraRepository;
import com.memoirist.media.Media;
import com.memoirist.media.MediaRepository;
import com.memoirist.storage.BucketBootstrap;
import com.memoirist.storage.StorageService;
import com.memoirist.tag.Tag;
import com.memoirist.tag.TagRepository;
import com.memoirist.template.Template;
import com.memoirist.template.TemplateRepository;
import com.memoirist.user.DevHeaderCurrentUserProvider;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeekCalendar;
import com.memoirist.week.WeekService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Fills the dev user's journal with sample content when the app starts on a developer's machine, so the feed has
 * something in it while the screens are built (task 3.16, PRD "Dev user").
 *
 * <p><b>It exists only when both are true</b>: the {@code local} profile is active, and
 * {@code memoirist.sample-content=true} (in {@code .env} as {@code MEMOIRIST_SAMPLE_CONTENT=true}, or on the command
 * line). In the {@code test} and {@code prod} profiles the bean is not created, so a test database or a deployment
 * never gets it ({@code SampleContentBeanTest}).
 *
 * <p><b>What it makes</b> (dates are counted back from the user's today, so the feed always looks current): four tags,
 * a template, and entries in the current week and the weeks before it, plus one old diary page. Three Eras overlap,
 * one inside another and one only partly (D-22). Among the entries are one started from the template, a diary-page
 * photo dated months back, a photo-only entry, a sealed Loot Drop, an opened one with a reply (D-33), an entry with a
 * soundtrack, and one in Recently deleted. The trip Era has a cover (D-36). The pictures are drawn by
 * {@link PlaceholderImages} and stored through {@link StorageService}, so they come back as presigned URLs like any photo.
 *
 * <p><b>Running it twice changes nothing.</b> It does nothing when the dev user already owns an entry (Recently deleted
 * ones included), Era, tag or template. So it fills an empty journal once and never adds to, or overwrites, one that
 * has been used. Its ids are made from fixed names, and the pictures are stored under keys made from them, so even an
 * interrupted run is finished by the next one without leaving a second copy.
 *
 * <p><b>Why it writes through repositories and not the services.</b> The services ask {@code CurrentUserProvider}
 * who is calling, and that answer comes from an HTTP request, which a startup job doesn't have. The sample content
 * therefore does not go through the services' rules, so it checks its own captions (with the same
 * {@link EntryContentValidator} the API uses) and a test reads everything back through the API.
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "memoirist.sample-content", havingValue = "true")
public class SampleContentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleContentSeeder.class);

    /** A Spotify track (D-35). The value is only a string; it is stored as it is and nothing here calls Spotify. */
    static final String SOUNDTRACK = "spotify:track:4cOdK2wGLETKBW3PvgPWqT";

    /** How many entries {@link #insertRows} creates, the one in Recently deleted included. Only the log line uses it. */
    static final int ENTRY_COUNT = 11;

    private static final String PNG = PlaceholderImages.CONTENT_TYPE;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final UserRepository users;
    private final TagRepository tags;
    private final TemplateRepository templates;
    private final EntryRepository entries;
    private final MediaRepository media;
    private final EraRepository eras;
    private final WeekService weeks;
    private final StorageService storage;
    private final BucketBootstrap bucket;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public SampleContentSeeder(
            UserRepository users,
            TagRepository tags,
            TemplateRepository templates,
            EntryRepository entries,
            MediaRepository media,
            EraRepository eras,
            WeekService weeks,
            StorageService storage,
            BucketBootstrap bucket,
            JdbcClient jdbc,
            Clock clock,
            PlatformTransactionManager transactions) {
        this.users = users;
        this.tags = tags;
        this.templates = templates;
        this.entries = entries;
        this.media = media;
        this.eras = eras;
        this.weeks = weeks;
        this.storage = storage;
        this.bucket = bucket;
        this.jdbc = jdbc;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /**
     * Creates the sample content for the dev user, unless the dev user already has content.
     *
     * @return {@code true} if this call created it, {@code false} if it found content and left everything alone
     * @throws IllegalStateException if the dev user isn't in the database (the {@code db/seed-local} migration makes it)
     */
    public boolean seed() {
        // The bucket may not exist yet: runners of the same kind start in no promised order, so don't rely on it.
        bucket.ensureBucket();
        User user = users.findById(DevHeaderCurrentUserProvider.DEV_USER_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "The dev user isn't in the database. Is the db/seed-local Flyway location on?"));
        if (alreadyHasContent(user.getId())) {
            log.info("Sample content skipped: the dev user already has entries, Eras, tags or templates.");
            return false;
        }

        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant now = Instant.now(clock);

        // Files first, rows second, as in an upload (TECH_STACK, Storage): a run that stops in between leaves files
        // with nothing pointing at them, and the next run stores them again under the same keys.
        Map<String, Image> images = images(user.getId());
        for (Image image : images.values()) {
            storage.put(image.objectKey(), image.bytes(), PNG);
        }
        transaction.executeWithoutResult(status -> insertRows(user.getId(), today, zone, now, images));
        log.info("Sample content created for the dev user: {} entries, 3 Eras, 4 tags, 1 template.", ENTRY_COUNT);
        return true;
    }

    // TODO(human) PRACTICE: Write alreadyHasContent: the guard that makes "run it twice, nothing changes" true. It must
    //   say yes when the user owns ANY row of the four kinds this class creates, including an entry that sits in
    //   Recently deleted, and it must look at one user only. Decide which tables to look at, and why asking for a
    //   count is the wrong tool when you only need to know whether there is at least one row.
    // Hint: D-30: @SQLRestriction hides deleted entries from JPA queries, so ask the table with SQL (JdbcClient), which
    //   Hibernate does not rewrite. EXISTS stops at the first row; COUNT(*) reads them all. One statement, four EXISTS
    //   joined by OR, with a named parameter for the user.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    private boolean alreadyHasContent(UUID userId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM entries WHERE user_id = :userId)
                            OR EXISTS (SELECT 1 FROM eras WHERE user_id = :userId)
                            OR EXISTS (SELECT 1 FROM tags WHERE user_id = :userId)
                            OR EXISTS (SELECT 1 FROM templates WHERE user_id = :userId)
                        """).param("userId", userId).query(Boolean.class).single();
    }
    // └── END ANSWER ▲ ──

    // ---- The pictures ---------------------------------------------------------------------------------------------

    /** One generated picture, the entry it belongs to and the row it will get. */
    private record Image(UUID mediaId, UUID entryId, String objectKey, String purpose, byte[] bytes) {}

    /** Draws every picture and works out where it is stored. The map keeps the order they are listed in here. */
    private static Map<String, Image> images(UUID userId) {
        Map<String, Image> images = new LinkedHashMap<>();
        add(images, userId, "hobart", "trip-day-one", Media.PURPOSE_PHOTO, PlaceholderImages.scene(0.55f));
        add(images, userId, "cradle", "trip-cradle-mountain", Media.PURPOSE_PHOTO, PlaceholderImages.scene(0.38f));
        add(images, userId, "page", "diary-page", Media.PURPOSE_PAGE_SCAN, PlaceholderImages.diaryPage(7));
        add(images, userId, "dump-1", "photo-only", Media.PURPOSE_PHOTO, PlaceholderImages.scene(0.08f));
        add(images, userId, "dump-2", "photo-only", Media.PURPOSE_PHOTO, PlaceholderImages.scene(0.72f));
        add(images, userId, "sealed", "sealed-letter", Media.PURPOSE_PHOTO, PlaceholderImages.scene(0.92f));
        return images;
    }

    private static void add(
            Map<String, Image> images, UUID userId, String name, String entryName, String purpose, byte[] bytes) {
        UUID mediaId = id("media:" + name);
        UUID entryId = id("entry:" + entryName);
        // The key shape of MediaService: users/{userId}/entries/{entryId}/{mediaId}.{ext}
        String objectKey = "users/" + userId + "/entries/" + entryId + "/" + mediaId + ".png";
        images.put(name, new Image(mediaId, entryId, objectKey, purpose, bytes));
    }

    // ---- The rows -------------------------------------------------------------------------------------------------

    /** Everything in one transaction: all of it is there, or none of it. */
    private void insertRows(UUID userId, LocalDate today, ZoneId zone, Instant now, Map<String, Image> images) {
        LocalDate thisMonday = WeekCalendar.firstDayOf(today);
        LocalDate tripDay = thisMonday.minusWeeks(3).plusDays(1);

        Tag trip = tags.saveAndFlush(new Tag(id("tag:trip"), userId, "Trip"));
        Tag work = tags.saveAndFlush(new Tag(id("tag:work"), userId, "Work"));
        Tag family = tags.saveAndFlush(new Tag(id("tag:family"), userId, "Family"));
        Tag gratitude = tags.saveAndFlush(new Tag(id("tag:gratitude"), userId, "Gratitude"));

        Template tripDayTemplate = templates.saveAndFlush(new Template(
                id("template:trip-day"),
                userId,
                "Trip day",
                List.of("Where did we go?", "Best moment", "Food", "Tomorrow")));

        // Three weeks ago, the trip: one entry started from the template (its headings are the template's, and the
        // empty "Tomorrow" prompt was dropped on save, D-06), and one with a soundtrack.
        Entry dayOne = entry(
                userId,
                "trip-day-one",
                tripDay,
                "Day one in Hobart",
                doc(
                        heading("Where did we go?"),
                        paragraph("Landed at nine, wandered the waterfront and ended up in a market by lunchtime."),
                        heading("Best moment"),
                        paragraph(
                                "Sunset from the top of the hill, with a coffee that went cold because nobody wanted to leave."),
                        heading("Food"),
                        paragraph("Scallop pie. Worth the queue.")));
        dayOne.setTemplateId(tripDayTemplate.getId());
        dayOne.replaceTags(List.of(trip));
        save(dayOne, images.get("hobart"));

        Entry cradle = entry(
                userId,
                "trip-cradle-mountain",
                tripDay.plusDays(2),
                "Cradle Mountain",
                doc(paragraph(
                        "Four hours of driving with one song on repeat. Worth every minute once the cloud lifted.")));
        cradle.setSoundtrack(SOUNDTRACK);
        cradle.replaceTags(List.of(trip, gratitude));
        save(cradle, images.get("cradle"));

        // Two weeks ago: an ordinary entry, and one that was deleted a few days ago and waits in Recently deleted
        // (D-30).
        Entry teamLunch = entry(
                userId,
                "team-lunch",
                thisMonday.minusWeeks(2).plusDays(2),
                "First team lunch",
                doc(
                        paragraph(
                                "Everyone ordered the same thing, so I did too. Nobody mentioned it, which I took as a good sign.")));
        teamLunch.replaceTags(List.of(work, gratitude));
        save(teamLunch);

        Entry deleted = entry(
                userId,
                "deleted-draft",
                thisMonday.minusWeeks(2).plusDays(3),
                "A draft I didn't keep",
                doc(paragraph("Too angry to be fair. Better left in Recently deleted for a while.")));
        deleted.setDeletedAt(now.minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MICROS));
        save(deleted);

        // Last week: photos with nothing written, a sealed Loot Drop, and a plain entry on the Sunday.
        save(
                entry(userId, "photo-only", thisMonday.minusWeeks(1).plusDays(1), null, null),
                images.get("dump-1"),
                images.get("dump-2"));

        Entry sealed = entry(
                userId,
                "sealed-letter",
                thisMonday.minusWeeks(1).plusDays(3),
                "For the end of the year",
                doc(paragraph("If you are reading this, the project shipped. I hope you remembered to rest.")));
        sealed.setUnlockDate(today.plusDays(60).atTime(9, 0).atZone(zone).toInstant());
        save(sealed, images.get("sealed"));

        Entry sunday = entry(
                userId,
                "sunday-reset",
                thisMonday.minusDays(1),
                "Sunday reset",
                doc(paragraph("Cleaned the flat, called Mum, planned the week on the back of an envelope.")));
        sunday.replaceTags(List.of(family));
        save(sunday);

        // This week.
        Entry todayEntry = entry(
                userId,
                "today",
                today,
                "Quiet day",
                doc(
                        paragraph("Nothing happened, and that was lovely."),
                        paragraph("Made soup. Read a chapter. Went to bed early.")));
        todayEntry.replaceTags(List.of(family, gratitude));
        save(todayEntry);

        // Six weeks ago: a letter that was sealed then and opened five days ago, so it is in the Opened strip. A reply
        // to
        // it, written today, is the "reply to past you" (D-33). And a page from a paper diary, dated months back
        // (D-20).
        Entry openedLetter = entry(
                userId,
                "opened-letter",
                thisMonday.minusWeeks(6).plusDays(2),
                "Letter to future me",
                doc(
                        paragraph(
                                "By the time you read this you will have moved, changed jobs and changed your mind about pineapple.")));
        openedLetter.setUnlockDate(today.minusDays(5).atTime(9, 0).atZone(zone).toInstant());
        save(openedLetter);

        Entry reply = entry(
                userId,
                "reply-to-past-me",
                today,
                "Dear past me",
                doc(paragraph("You were right about the job and wrong about the pineapple.")));
        reply.setReplyToEntryId(openedLetter.getId());
        save(reply);

        save(
                entry(userId, "diary-page", thisMonday.minusWeeks(14).plusDays(4), "From my paper diary", null),
                images.get("page"));

        // Eras last, because the trip's cover is one of the photos above. Three overlap (D-22):
        //  - Uni Days ends in the middle of last week;
        //  - Tasmania sits wholly inside Uni Days (the feed nests it, one level deep);
        //  - New job starts two weeks ago and has no end: it overlaps Uni Days only partly, so the weeks both hold
        //    get the other Era as a chip.
        Era uni = new Era(id("era:uni-days"), userId, "Uni Days", thisMonday.minusDays(400));
        uni.setDescription("Lectures, group projects and far too many late nights.");
        uni.setEndDate(thisMonday.minusWeeks(1).plusDays(2));
        eras.saveAndFlush(uni);

        Era tasmania = new Era(id("era:tasmania"), userId, "Tasmania", tripDay);
        tasmania.setDescription("A week away: Hobart, the market and a very long drive.");
        tasmania.setEndDate(tripDay.plusDays(4));
        tasmania.setCoverMediaId(images.get("hobart").mediaId());
        eras.saveAndFlush(tasmania);

        Era newJob = new Era(id("era:new-job"), userId, "New job", thisMonday.minusWeeks(2));
        newJob.setDescription("Still finding where the good coffee is.");
        eras.saveAndFlush(newJob);
    }

    private Entry entry(UUID userId, String name, LocalDate date, String title, JsonNode caption) {
        // Find the week first: it runs a native INSERT, and nothing of this entry should be pending when it does.
        UUID weekId = weeks.findOrCreate(userId, date).getId();
        Entry entry = new Entry(id("entry:" + name), userId, weekId, date);
        entry.setTitle(title);
        if (caption != null) {
            EntryContentValidator.validate(caption);
            ContentTextExtractor.Extracted extracted = ContentTextExtractor.extract(caption);
            entry.setContentJson(caption);
            entry.setContentText(extracted.text());
            entry.setPreviewLine(extracted.previewLine());
        }
        return entry;
    }

    /** Saves the entry and then its pictures, in the order given. */
    private void save(Entry entry, Image... pictures) {
        Entry saved = entries.saveAndFlush(entry);
        for (int position = 0; position < pictures.length; position++) {
            Image picture = pictures[position];
            media.saveAndFlush(new Media(
                    picture.mediaId(),
                    saved,
                    picture.objectKey(),
                    PNG,
                    picture.purpose(),
                    position,
                    picture.bytes().length));
        }
    }

    // ---- Ids and captions -----------------------------------------------------------------------------------------

    /**
     * The same id for the same name on every run and every machine: a name-based (version 3) UUID. It keeps the object
     * keys of the pictures the same too, so storing one again replaces the file instead of adding a second.
     */
    static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("memoirist-sample:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static ObjectNode paragraph(String words) {
        ObjectNode paragraph = JSON.createObjectNode().put("type", "paragraph");
        paragraph.putArray("content").addObject().put("type", "text").put("text", words);
        return paragraph;
    }

    private static ObjectNode heading(String words) {
        ObjectNode heading = JSON.createObjectNode().put("type", "heading");
        heading.putObject("attrs").put("level", 2);
        heading.putArray("content").addObject().put("type", "text").put("text", words);
        return heading;
    }

    private static ObjectNode doc(ObjectNode... blocks) {
        ObjectNode doc = JSON.createObjectNode().put("type", "doc");
        ArrayNode content = doc.putArray("content");
        for (ObjectNode block : blocks) {
            content.add(block);
        }
        return doc;
    }
}
