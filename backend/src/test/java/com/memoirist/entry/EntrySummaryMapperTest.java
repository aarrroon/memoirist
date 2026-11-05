package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.memoirist.Fixtures;
import com.memoirist.entry.dto.EntrySummaryResponse;
import com.memoirist.media.Media;
import com.memoirist.media.MediaKind;
import com.memoirist.media.MediaMapper;
import com.memoirist.storage.StorageService;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * The list row of an entry (task 3.9, D-10, D-24), as plain logic with a fixed {@code now} and no database. The same
 * rules are checked end to end, through the API, in {@link EntryListTest}.
 *
 * <p>As in {@link EntryMapperTest}, the entry has something private in <em>every</em> field, so a field that leaks has
 * a value to leak.
 */
class EntrySummaryMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-31T01:00:00Z");
    private static final Instant TOMORROW = NOW.plusSeconds(86_400);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The only fields of a sealed row that are not private (D-10, API_CONTRACT EntrySummary). */
    private static final Set<String> VISIBLE_WHILE_SEALED =
            Set.of("id", "weeklyLogId", "entryDate", "createdAt", "unlockDate", "locked", "dateUnknown", "deletedAt");

    private final StorageService storage = mock(StorageService.class);
    private final EntryMapper mapper = new EntryMapper(new MediaMapper(storage));

    private Entry entry;

    @BeforeEach
    void anEntryWithSomethingPrivateInEveryField() {
        UUID userId = UUID.randomUUID();
        entry = new Entry(UUID.randomUUID(), userId, UUID.randomUUID(), LocalDate.parse("2026-10-20"));
        entry.setTitle("Secret title");
        entry.setContentJson(JSON.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":"
                + "[{\"type\":\"text\",\"text\":\"Secret words\"}]}]}"));
        entry.setContentText("Secret words");
        entry.setPreviewLine("Secret words");
        entry.setDateUnknown(true);
        ReflectionTestUtils.setField(entry, "createdAt", Instant.parse("2026-10-20T03:30:00Z"));
        entry.replaceTags(Set.of(Fixtures.tag(userId, "Zebra"), Fixtures.tag(userId, "apple")));
        addMedia(entry, "image/jpeg", Media.PURPOSE_PHOTO, 0);
        addMedia(entry, "image/png", Media.PURPOSE_PAGE_SCAN, 1);
        addMedia(entry, "audio/webm", Media.PURPOSE_PHOTO, 2);
    }

    @SuppressWarnings("unchecked")
    private static void addMedia(Entry entry, String mediaType, String purpose, int order) {
        Media media = new Media(UUID.randomUUID(), entry, "users/u/secret-" + order, mediaType, purpose, order, 2048);
        ((List<Media>) ReflectionTestUtils.getField(entry, "media")).add(media);
    }

    // Open entries

    @Test
    void anOpenEntryShowsItsTitlePreviewCountsAndTags() {
        EntrySummaryResponse row = mapper.toSummary(entry, NOW);

        assertThat(row.id()).isEqualTo(entry.getId());
        assertThat(row.weeklyLogId()).isEqualTo(entry.getWeeklyLogId());
        assertThat(row.entryDate()).isEqualTo(LocalDate.parse("2026-10-20"));
        assertThat(row.title()).isEqualTo("Secret title");
        assertThat(row.previewLine()).isEqualTo("Secret words");
        assertThat(row.photoCount()).as("a photo and a diary page").isEqualTo(2);
        assertThat(row.voiceNoteCount()).isEqualTo(1);
        assertThat(row.firstMediaKind()).isEqualTo(MediaKind.PHOTO);
        assertThat(row.tags())
                .extracting("name", "source")
                .containsExactly(tuple("apple", "USER"), tuple("Zebra", "USER"));
        assertThat(row.createdAt()).isEqualTo(Instant.parse("2026-10-20T03:30:00Z"));
        assertThat(row.unlockDate()).isNull();
        assertThat(row.locked()).isFalse();
        assertThat(row.dateUnknown()).isTrue();
        assertThat(row.deletedAt()).isNull();
    }

    @Test
    void aRowNeverSignsAFileUrlBecauseItCarriesNoMedia() {
        mapper.toSummary(entry, NOW);

        verifyNoInteractions(storage);
    }

    @Test
    void anOpenEntryWithNoMediaHasZerosAndNoKind() {
        Entry bare = new Entry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2026-10-20"));

        EntrySummaryResponse row = mapper.toSummary(bare, NOW);

        assertThat(row.photoCount()).isZero();
        assertThat(row.voiceNoteCount()).isZero();
        assertThat(row.firstMediaKind()).isNull();
        assertThat(row.tags()).isEmpty();
        assertThat(row.title()).isNull();
        assertThat(row.previewLine()).isNull();
    }

    @Test
    void aRowCarriesTheDeletionTimeWhenThereIsOne() {
        Instant deleted = Instant.parse("2026-10-30T00:00:00Z");
        entry.setDeletedAt(deleted);

        assertThat(mapper.toSummary(entry, NOW).deletedAt()).isEqualTo(deleted);
    }

    @Test
    void anOpenRowSerialisesAsIsLockedFalseAndTheKindAsItsName() {
        String json = JSON.writeValueAsString(mapper.toSummary(entry, NOW));

        assertThat(json)
                .contains("\"isLocked\":false")
                .doesNotContain("\"locked\"")
                .contains("\"firstMediaKind\":\"PHOTO\"")
                .contains("\"photoCount\":2");
    }

    @Test
    void aLootDropThatHasOpenedShowsEverythingAgain() {
        Instant openedAt = NOW.minusSeconds(60);
        entry.setUnlockDate(openedAt);

        EntrySummaryResponse row = mapper.toSummary(entry, NOW);

        assertThat(row.locked()).isFalse();
        assertThat(row.unlockDate()).isEqualTo(openedAt);
        assertThat(row.title()).isEqualTo("Secret title");
        assertThat(row.photoCount()).isEqualTo(2);
    }

    @Test
    void aLootDropIsOpenAtTheExactInstantOfItsUnlockDate() {
        entry.setUnlockDate(NOW);

        assertThat(mapper.toSummary(entry, NOW).locked()).isFalse();
        assertThat(mapper.toSummary(entry, NOW.minusNanos(1_000)).locked()).isTrue();
    }

    // Sealed entries

    @Test
    void aSealedEntryShowsOnlyTheFieldsThatAreNotPrivate() {
        entry.setUnlockDate(TOMORROW);

        EntrySummaryResponse row = mapper.toSummary(entry, NOW);

        assertThat(row.id()).isEqualTo(entry.getId());
        assertThat(row.weeklyLogId()).isEqualTo(entry.getWeeklyLogId());
        assertThat(row.entryDate()).isEqualTo(LocalDate.parse("2026-10-20"));
        assertThat(row.createdAt()).isEqualTo(Instant.parse("2026-10-20T03:30:00Z"));
        assertThat(row.unlockDate()).isEqualTo(TOMORROW);
        assertThat(row.locked()).isTrue();
        assertThat(row.dateUnknown()).isTrue();

        assertThat(row.title()).isNull();
        assertThat(row.previewLine()).isNull();
        assertThat(row.photoCount()).isNull();
        assertThat(row.voiceNoteCount()).isNull();
        assertThat(row.firstMediaKind()).isNull();
        assertThat(row.tags()).isEmpty();
    }

    @Test
    void everyFieldOfTheRowThatIsNotOnTheVisibleListIsEmptyWhileSealed() {
        // The guard for the future: add a component to EntrySummaryResponse and, unless it is named in
        // VISIBLE_WHILE_SEALED on purpose, this fails the moment the sealed branch gives it a value.
        entry.setUnlockDate(TOMORROW);
        EntrySummaryResponse row = mapper.toSummary(entry, NOW);

        Set<String> names = new HashSet<>();
        for (RecordComponent component : EntrySummaryResponse.class.getRecordComponents()) {
            names.add(component.getName());
            if (VISIBLE_WHILE_SEALED.contains(component.getName())) {
                continue;
            }
            Object value = ReflectionTestUtils.invokeGetterMethod(row, component.getName());
            boolean empty = value == null || (value instanceof Collection<?> c && c.isEmpty());
            assertThat(empty)
                    .as("%s must be empty while sealed, but was %s", component.getName(), value)
                    .isTrue();
        }
        assertThat(names).as("a stale name in VISIBLE_WHILE_SEALED").containsAll(VISIBLE_WHILE_SEALED);
    }

    @Test
    void nothingPrivateAppearsAnywhereInTheSealedJson() {
        entry.setUnlockDate(TOMORROW);

        String json = JSON.writeValueAsString(mapper.toSummary(entry, NOW));

        assertThat(json)
                .doesNotContain("Secret")
                .doesNotContain("secret")
                .doesNotContain("Zebra")
                .doesNotContain("apple")
                .doesNotContain("PHOTO")
                .doesNotContain("PAGE_SCAN")
                .doesNotContain("VOICE_NOTE")
                .contains("\"isLocked\":true");
    }

    @Test
    void aSealedEntrysTitleMediaAndTagsAreNeverReadForARow() {
        // No query for an entry nobody may see: the lazy collections are not even touched.
        Entry watched = spy(entry);
        watched.setUnlockDate(TOMORROW);

        mapper.toSummary(watched, NOW);

        verify(watched, never()).getMedia();
        verify(watched, never()).getTags();
        verify(watched, never()).getTitle();
        verify(watched, never()).getPreviewLine();
        verify(watched, never()).getContentJson();
        verify(watched, never()).getContentText();
    }
}
