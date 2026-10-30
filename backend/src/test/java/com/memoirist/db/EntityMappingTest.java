package com.memoirist.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.entry.Entry;
import com.memoirist.entry.EntryRepository;
import com.memoirist.era.Era;
import com.memoirist.era.EraRepository;
import com.memoirist.media.Media;
import com.memoirist.media.MediaRepository;
import com.memoirist.tag.Tag;
import com.memoirist.tag.TagRepository;
import com.memoirist.template.Template;
import com.memoirist.template.TemplateRepository;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * That the seven entities and the V1 schema agree (task 2.4).
 *
 * <p>{@code ddl-auto=validate} already refuses to start the application when an entity maps a table or column that
 * V1 doesn't have, and every {@code @IntegrationTest} would fail at startup. What it doesn't check is whether a value
 * survives the trip: that a JSON document is stored as a JSON object and not as a string that contains one, that an
 * {@code Instant} comes back as the same instant, or that a column Hibernate never writes keeps its default. Each test
 * here saves an entity with every column filled in, empties the persistence context so that the next read has to go to
 * Postgres, and compares.
 */
@IntegrationTest
@Transactional
class EntityMappingTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    UserRepository users;

    @Autowired
    WeeklyLogRepository weeks;

    @Autowired
    TemplateRepository templates;

    @Autowired
    EraRepository eras;

    @Autowired
    TagRepository tags;

    @Autowired
    EntryRepository entries;

    @Autowired
    MediaRepository media;

    @Test
    void exactlyThePhase1EntitiesAreMapped() {
        // No week-Era entity (D-22) and no entry-tag entity: entry_tags is a join table (SPRINT_PLAN 2.4).
        Set<String> mapped = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(EntityType::getName)
                .collect(Collectors.toSet());

        assertThat(mapped).containsExactlyInAnyOrder("User", "Era", "Template", "WeeklyLog", "Entry", "Media", "Tag");
    }

    @Test
    void hibernateStoresJsonWithJackson3() {
        // Both Jackson 2 (a transitive dependency of springdoc) and Jackson 3 are on the classpath. The entity's
        // JsonNode is a Jackson 3 type, so a Jackson 2 mapper would write it as a bean, not as the document.
        var mapper = entityManagerFactory
                .unwrap(SessionFactoryImplementor.class)
                .getTypeConfiguration()
                .getJsonFormatMapper();

        assertThat(mapper.getClass().getName()).isEqualTo("org.hibernate.type.format.jackson.Jackson3JsonFormatMapper");
    }

    @Test
    void theSeededDevUserLoads() {
        User dev = users.findById(Fixtures.DEV_USER_ID).orElseThrow();

        assertThat(dev.getEmail()).isEqualTo("dev@memoirist.local");
        assertThat(dev.getDisplayName()).isEqualTo("Dev User");
        assertThat(dev.getTimezone()).isEqualTo("Australia/Melbourne");
        assertThat(dev.getBirthday()).isNull();
        assertThat(dev.getCreatedAt())
                .as("filled in by the column default NOW()")
                .isNotNull();
        assertThat(dev.isNew()).isFalse();
    }

    @Test
    void aUserRoundTrips() {
        User user = Fixtures.user();
        user.setDisplayName("Aaron");
        user.setTimezone("Pacific/Auckland");
        user.setBirthday(LocalDate.of(1998, 2, 28));

        User loaded = saveClearAndLoad(user, () -> users.findById(user.getId()).orElseThrow());

        assertThat(loaded)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(user);
        assertTimestamps(loaded);
    }

    @Test
    void anEraRoundTrips() {
        User user = users.save(Fixtures.user());
        Era era = new Era(UUID.randomUUID(), user.getId(), "Uni Days", LocalDate.of(2022, 3, 1));
        era.setDescription("Three years in the library, mostly.");
        era.setEndDate(LocalDate.of(2024, 11, 29));
        era.setSoundtrack("spotify:playlist:37i9dQZF1DX4WYpdgoIcn6");
        era.setPinnedInFeed(true);

        Era loaded = saveClearAndLoad(
                era, () -> eras.findByIdAndUserId(era.getId(), user.getId()).orElseThrow());

        assertThat(loaded)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(era);
        assertTimestamps(loaded);
    }

    @Test
    void anEraWithNoEndDateIsOngoing() {
        User user = users.save(Fixtures.user());
        Era era = new Era(UUID.randomUUID(), user.getId(), "Now", LocalDate.of(2026, 1, 1));

        Era loaded = saveClearAndLoad(
                era, () -> eras.findByIdAndUserId(era.getId(), user.getId()).orElseThrow());

        assertThat(loaded.getEndDate()).isNull();
        assertThat(loaded.isPinnedInFeed()).isFalse();
    }

    @Test
    void aTemplateRoundTripsAndItsHeadingsAreAJsonArray() {
        User user = users.save(Fixtures.user());
        Template template = new Template(
                UUID.randomUUID(), user.getId(), "Evening reflection", List.of("What went well?", "What didn't?"));

        Template loaded = saveClearAndLoad(
                template,
                () -> templates
                        .findByIdAndUserId(template.getId(), user.getId())
                        .orElseThrow());

        assertThat(loaded.getSubheadings()).containsExactly("What went well?", "What didn't?");
        assertThat(loaded.getName()).isEqualTo("Evening reflection");
        assertThat(jdbc.sql("SELECT jsonb_typeof(subheadings) || ':' || jsonb_array_length(subheadings) FROM templates"
                                + " WHERE id = :id")
                        .param("id", template.getId())
                        .query(String.class)
                        .single())
                .as("stored as a real JSON array, not as a string")
                .isEqualTo("array:2");
    }

    @Test
    void aWeekRoundTrips() {
        User user = users.save(Fixtures.user());
        WeeklyLog week = Fixtures.week(user.getId());
        week.setTitle("The week of the move");
        week.setClosed(true);
        week.setSoundtrack("spotify:track:4uLU6hMCjMI75M1A2tKUQC");

        WeeklyLog loaded = saveClearAndLoad(
                week, () -> weeks.findByIdAndUserId(week.getId(), user.getId()).orElseThrow());

        assertThat(loaded)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(week);
        assertThat(loaded.getEndDate()).as("start + 6").isEqualTo(Fixtures.MONDAY.plusDays(6));
        assertTimestamps(loaded);
    }

    @Test
    void aTagRoundTrips() {
        User user = users.save(Fixtures.user());
        Tag tag = Fixtures.tag(user.getId(), "Going out");

        Tag loaded = saveClearAndLoad(
                tag, () -> tags.findByIdAndUserId(tag.getId(), user.getId()).orElseThrow());

        assertThat(loaded)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(tag);
        assertTimestamps(loaded);
    }

    @Test
    void anEntryRoundTripsWithEveryColumnFilled() throws Exception {
        User user = users.save(Fixtures.user());
        WeeklyLog week = weeks.save(Fixtures.week(user.getId()));
        Template template = templates.save(new Template(UUID.randomUUID(), user.getId(), "Morning", List.of("Goals")));
        Entry lootDrop = entries.save(Fixtures.entry(user.getId(), week));
        JsonNode caption = JSON.readTree("""
                {"type":"doc","content":[
                  {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Goals"}]},
                  {"type":"paragraph","content":[{"type":"text","text":"Ship the schema","marks":[{"type":"bold"}]}]}]}
                """);

        Entry entry = new Entry(UUID.randomUUID(), user.getId(), week.getId(), LocalDate.of(2026, 10, 28));
        entry.setTemplateId(template.getId());
        entry.setTitle("A good day");
        entry.setContentJson(caption);
        entry.setContentText("Goals Ship the schema");
        entry.setPreviewLine("Ship the schema");
        entry.setSoundtrack("spotify:track:4uLU6hMCjMI75M1A2tKUQC");
        entry.setReplyToEntryId(lootDrop.getId());
        entry.setUnlockDate(Instant.parse("2027-01-01T22:00:00.123456Z"));
        entry.setUnlockNotified(true);
        entry.setUnlockSeenAt(Instant.parse("2027-01-02T01:02:03.654321Z"));
        entry.setDateUnknown(true);

        Entry loaded = saveClearAndLoad(
                entry,
                () -> entries.findByIdAndUserId(entry.getId(), user.getId()).orElseThrow());

        assertThat(loaded)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt", "media", "tags", "contentJson")
                .isEqualTo(entry);
        assertThat(loaded.getContentJson()).isEqualTo(caption);
        assertThat(loaded.getVersion()).isZero();
        assertTimestamps(loaded);
        assertThat(jdbc.sql("SELECT jsonb_typeof(content_json) || ':' || (content_json ->> 'type') FROM entries"
                                + " WHERE id = :id")
                        .param("id", entry.getId())
                        .query(String.class)
                        .single())
                .as("stored as a JSON object, not as a JSON string that contains one")
                .isEqualTo("object:doc");
    }

    @Test
    void anEntryWithNoCaptionStoresSqlNullNotJsonNull() {
        User user = users.save(Fixtures.user());
        WeeklyLog week = weeks.save(Fixtures.week(user.getId()));
        Entry entry = Fixtures.entry(user.getId(), week);

        Entry loaded = saveClearAndLoad(
                entry,
                () -> entries.findByIdAndUserId(entry.getId(), user.getId()).orElseThrow());

        assertThat(loaded.getContentJson()).isNull();
        assertThat(jdbc.sql("SELECT count(*) FROM entries WHERE id = :id AND content_json IS NULL")
                        .param("id", entry.getId())
                        .query(Long.class)
                        .single())
                .as("D-05: NULL means no caption")
                .isEqualTo(1L);
    }

    @Test
    void anEntryInsertedBySqlLoadsWithTheColumnDefaults() {
        // The columns Hibernate writes explicitly don't prove the defaults. A row written by plain SQL (a future
        // import or a seed) takes them, and the entity has to read them as they are.
        User user = users.save(Fixtures.user());
        WeeklyLog week = weeks.save(Fixtures.week(user.getId()));
        em.flush();
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO entries (id, user_id, weekly_log_id, entry_date) VALUES (:id, :user, :week, :date)")
                .param("id", id)
                .param("user", user.getId())
                .param("week", week.getId())
                .param("date", LocalDate.of(2026, 10, 28))
                .update();
        em.clear();

        Entry loaded = entries.findByIdAndUserId(id, user.getId()).orElseThrow();

        assertThat(loaded.getContentText()).isEmpty();
        assertThat(loaded.getContentJson()).isNull();
        assertThat(loaded.getTitle()).isNull();
        assertThat(loaded.getVersion()).isZero();
        assertThat(loaded.isUnlockNotified()).isFalse();
        assertThat(loaded.isDateUnknown()).isFalse();
        assertThat(loaded.getDeletedAt()).isNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void aMediaItemRoundTripsAndKeepsOnlyItsUploadedAt() {
        User user = users.save(Fixtures.user());
        WeeklyLog week = weeks.save(Fixtures.week(user.getId()));
        Entry entry = entries.save(Fixtures.entry(user.getId(), week));
        Media page = new Media(
                UUID.randomUUID(),
                entry,
                "users/" + user.getId() + "/entries/" + entry.getId() + "/page.png",
                "image/png",
                Media.PURPOSE_PAGE_SCAN,
                3,
                1_048_576L);

        Media loaded = saveClearAndLoad(
                page,
                () -> media.findByIdAndEntryUserId(page.getId(), user.getId()).orElseThrow());

        assertThat(loaded)
                .usingRecursiveComparison()
                .ignoringFields("uploadedAt", "entry")
                .isEqualTo(page);
        assertThat(loaded.getEntry().getId()).isEqualTo(entry.getId());
        assertThat(loaded.getUploadedAt()).as("filled in by @CreationTimestamp").isNotNull();
        assertThat(jdbc.sql("SELECT count(*) FROM information_schema.columns WHERE table_name = 'media'"
                                + " AND column_name IN ('created_at', 'updated_at')")
                        .query(Long.class)
                        .single())
                .as("Media extends BaseEntity because its table has no created_at or updated_at (D-12)")
                .isZero();
    }

    /** Saves, writes to Postgres, forgets everything in memory, and then reads the row back as a new object. */
    private <T> T saveClearAndLoad(T entity, java.util.function.Supplier<T> load) {
        em.persist(entity);
        em.flush();
        em.clear();
        T loaded = load.get();
        assertThat(loaded).as("a fresh instance, read from the database").isNotSameAs(entity);
        return loaded;
    }

    private static void assertTimestamps(com.memoirist.common.TimestampedEntity entity) {
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull().isAfterOrEqualTo(entity.getCreatedAt());
    }
}
