package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.memoirist.Fixtures;
import com.memoirist.entry.dto.EntryResponse;
import com.memoirist.media.Media;
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
 * Redaction (D-10, non-negotiable 4), as plain logic with a fixed {@code now} and no database. The same rule is
 * checked end to end, through the API, in {@code EntryRedactionTest}.
 *
 * <p>The mapper has two branches: an open entry shows everything, a sealed one shows an allow-list of fields. These
 * tests fill an entry with something private in <em>every</em> field, so a field that leaks has a value to leak.
 */
class EntryMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-31T01:00:00Z");
    private static final Instant TOMORROW = NOW.plusSeconds(86_400);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The only fields of a sealed entry that are not private (D-10, API_CONTRACT sealed example). */
    private static final Set<String> VISIBLE_WHILE_SEALED = Set.of(
            "id",
            "weeklyLogId",
            "entryDate",
            "createdAt",
            "updatedAt",
            "unlockDate",
            "locked",
            "dateUnknown",
            "version");

    private final StorageService storage = mock(StorageService.class);
    private final EntryMapper mapper = new EntryMapper(new MediaMapper(storage));

    private UUID templateId;
    private UUID replyTarget;
    private Entry entry;

    @BeforeEach
    void anEntryWithSomethingPrivateInEveryField() {
        when(storage.presignGet(anyString()))
                .thenAnswer(call -> "https://storage.example/signed/" + call.getArgument(0));

        UUID userId = UUID.randomUUID();
        templateId = UUID.randomUUID();
        replyTarget = UUID.randomUUID();
        entry = new Entry(UUID.randomUUID(), userId, UUID.randomUUID(), LocalDate.parse("2026-10-20"));
        entry.setTemplateId(templateId);
        entry.setTitle("Secret title");
        entry.setContentJson(JSON.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":"
                + "[{\"type\":\"text\",\"text\":\"Secret words\"}]}]}"));
        entry.setContentText("Secret words");
        entry.setPreviewLine("Secret words");
        entry.setSoundtrack("spotify:track:1a2B3c4D5e6F7g8H9i0J1k");
        entry.setReplyToEntryId(replyTarget);
        entry.setDateUnknown(true);
        ReflectionTestUtils.setField(entry, "createdAt", Instant.parse("2026-10-20T03:30:00Z"));
        ReflectionTestUtils.setField(entry, "updatedAt", Instant.parse("2026-10-21T09:05:00Z"));
        ReflectionTestUtils.setField(entry, "version", 4L);
        entry.replaceTags(Set.of(Fixtures.tag(userId, "Zebra"), Fixtures.tag(userId, "apple")));
        // In carousel order, as Hibernate loads them (Entry.media is @OrderBy displayOrder); the mapper keeps that
        // order.
        addMedia(entry, "users/u/entries/e/first.jpg", 0);
        addMedia(entry, "users/u/entries/e/secret-photo.jpg", 1);
    }

    @SuppressWarnings("unchecked")
    private static void addMedia(Entry entry, String key, int order) {
        Media media = new Media(UUID.randomUUID(), entry, key, "image/jpeg", Media.PURPOSE_PHOTO, order, 2048);
        ((List<Media>) ReflectionTestUtils.getField(entry, "media")).add(media);
    }

    // Open entries

    @Test
    void anOpenEntryShowsEverythingInTheContractShape() {
        EntryResponse response = mapper.toResponse(entry, NOW);

        assertThat(response.id()).isEqualTo(entry.getId());
        assertThat(response.weeklyLogId()).isEqualTo(entry.getWeeklyLogId());
        assertThat(response.entryDate()).isEqualTo(LocalDate.parse("2026-10-20"));
        assertThat(response.templateId()).isEqualTo(templateId);
        assertThat(response.title()).isEqualTo("Secret title");
        assertThat(response.contentJson()).isSameAs(entry.getContentJson());
        assertThat(response.contentText()).isEqualTo("Secret words");
        assertThat(response.soundtrack()).isEqualTo("spotify:track:1a2B3c4D5e6F7g8H9i0J1k");
        assertThat(response.replyToEntryId()).isEqualTo(replyTarget);
        assertThat(response.replyIds()).isEmpty();
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-10-20T03:30:00Z"));
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-10-21T09:05:00Z"));
        assertThat(response.unlockDate()).isNull();
        assertThat(response.locked()).isFalse();
        assertThat(response.dateUnknown()).isTrue();
        assertThat(response.version()).isEqualTo(4L);
    }

    @Test
    void anOpenEntrysTagsComeByNameWithTheUserSource() {
        EntryResponse response = mapper.toResponse(entry, NOW);

        assertThat(response.tags())
                .extracting("name", "source")
                .containsExactly(tuple("apple", "USER"), tuple("Zebra", "USER"));
    }

    @Test
    void anOpenEntrysMediaIsInOrderWithPresignedUrlsAndNoKeys() {
        EntryResponse response = mapper.toResponse(entry, NOW);

        assertThat(response.media()).hasSize(2);
        assertThat(response.media()).extracting("displayOrder").containsExactly(0, 1);
        assertThat(response.media().get(0).fileUrl())
                .isEqualTo("https://storage.example/signed/users/u/entries/e/first.jpg");
        assertThat(response.media()).allSatisfy(media -> {
            assertThat(media.entryId()).isEqualTo(entry.getId());
            assertThat(media.mediaType()).isEqualTo("image/jpeg");
            assertThat(media.purpose()).isEqualTo("PHOTO");
            assertThat(media.fileSizeBytes()).isEqualTo(2048L);
        });
    }

    @Test
    void anOpenEntrySerialisesAsIsLockedFalseAndNeverAsLocked() {
        String json = JSON.writeValueAsString(mapper.toResponse(entry, NOW));

        assertThat(json).contains("\"isLocked\":false").doesNotContain("\"locked\"");
    }

    @Test
    void aLootDropThatHasOpenedShowsEverythingAndItsUnlockDate() {
        Instant openedAt = NOW.minusSeconds(60);
        entry.setUnlockDate(openedAt);

        EntryResponse response = mapper.toResponse(entry, NOW);

        assertThat(response.locked()).isFalse();
        assertThat(response.unlockDate()).isEqualTo(openedAt);
        assertThat(response.title()).isEqualTo("Secret title");
        assertThat(response.media()).hasSize(2);
    }

    // Sealed entries

    @Test
    void aSealedEntryShowsOnlyTheFieldsThatAreNotPrivate() {
        entry.setUnlockDate(TOMORROW);

        EntryResponse response = mapper.toResponse(entry, NOW);

        assertThat(response.id()).isEqualTo(entry.getId());
        assertThat(response.weeklyLogId()).isEqualTo(entry.getWeeklyLogId());
        assertThat(response.entryDate()).isEqualTo(LocalDate.parse("2026-10-20"));
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-10-20T03:30:00Z"));
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-10-21T09:05:00Z"));
        assertThat(response.unlockDate()).isEqualTo(TOMORROW);
        assertThat(response.locked()).isTrue();
        assertThat(response.dateUnknown())
                .as("the memory date is not private (D-34)")
                .isTrue();
        assertThat(response.version()).isEqualTo(4L);

        assertThat(response.templateId()).isNull();
        assertThat(response.title()).isNull();
        assertThat(response.contentJson()).isNull();
        assertThat(response.contentText()).isNull();
        assertThat(response.soundtrack()).isNull();
        assertThat(response.replyToEntryId()).isNull();
        assertThat(response.tags()).isEmpty();
        assertThat(response.replyIds()).isEmpty();
        assertThat(response.media()).isEmpty();
    }

    @Test
    void everyFieldOfTheResponseThatIsNotOnTheVisibleListIsEmptyWhileSealed() {
        // The guard for the future: add a component to EntryResponse and, unless it is named in VISIBLE_WHILE_SEALED
        // on purpose, this fails the moment the sealed branch gives it a value.
        entry.setUnlockDate(TOMORROW);
        EntryResponse response = mapper.toResponse(entry, NOW);

        for (RecordComponent component : EntryResponse.class.getRecordComponents()) {
            if (VISIBLE_WHILE_SEALED.contains(component.getName())) {
                continue;
            }
            Object value = ReflectionTestUtils.invokeGetterMethod(response, component.getName());
            boolean empty = value == null || (value instanceof Collection<?> c && c.isEmpty());
            assertThat(empty)
                    .as("%s must be empty while sealed, but was %s", component.getName(), value)
                    .isTrue();
        }
        Set<String> names = new HashSet<>();
        for (RecordComponent component : EntryResponse.class.getRecordComponents()) {
            names.add(component.getName());
        }
        assertThat(names).as("a stale name in VISIBLE_WHILE_SEALED").containsAll(VISIBLE_WHILE_SEALED);
    }

    @Test
    void nothingPrivateAppearsAnywhereInTheSealedJson() {
        entry.setUnlockDate(TOMORROW);

        String json = JSON.writeValueAsString(mapper.toResponse(entry, NOW));

        assertThat(json)
                .doesNotContain("Secret")
                .doesNotContain("secret")
                .doesNotContain(templateId.toString())
                .doesNotContain(replyTarget.toString())
                .doesNotContain("spotify")
                .doesNotContain("Zebra")
                .doesNotContain("apple")
                .doesNotContain("first.jpg")
                .doesNotContain("storage.example")
                .contains("\"isLocked\":true");
    }

    @Test
    void aSealedEntrysMediaAndTagsAreNeverReadAndNoUrlIsSigned() {
        // No query and no signing for an entry nobody may see: the lazy collections are not even touched.
        Entry watched = spy(entry);
        watched.setUnlockDate(TOMORROW);

        mapper.toResponse(watched, NOW);

        verifyNoInteractions(storage);
        verify(watched, never()).getMedia();
        verify(watched, never()).getTags();
        verify(watched, never()).getTitle();
        verify(watched, never()).getContentJson();
        verify(watched, never()).getContentText();
    }

    @Test
    void anOpenEntryDoesPresignItsMedia() {
        mapper.toResponse(entry, NOW);

        verify(storage).presignGet("users/u/entries/e/first.jpg");
        verify(storage).presignGet("users/u/entries/e/secret-photo.jpg");
    }

    @Test
    void theSealIsStrictAtTheUnlockInstantTheEntryIsOpen() {
        entry.setUnlockDate(NOW);
        assertThat(mapper.toResponse(entry, NOW).locked())
                .as("at the unlock instant")
                .isFalse();

        entry.setUnlockDate(NOW.plusNanos(1));
        assertThat(mapper.toResponse(entry, NOW).locked())
                .as("one nanosecond before")
                .isTrue();

        entry.setUnlockDate(NOW.minusNanos(1));
        assertThat(mapper.toResponse(entry, NOW).locked())
                .as("one nanosecond after")
                .isFalse();
    }

    @Test
    void theSameEntryIsSealedThenOpenAsTheClockMoves() {
        entry.setUnlockDate(TOMORROW);

        assertThat(mapper.toResponse(entry, NOW).title()).isNull();
        assertThat(mapper.toResponse(entry, TOMORROW.minusSeconds(1)).title()).isNull();
        assertThat(mapper.toResponse(entry, TOMORROW).title()).isEqualTo("Secret title");
        assertThat(mapper.toResponse(entry, TOMORROW.plusSeconds(1)).media()).hasSize(2);
    }

    @Test
    void anEmptyOpenEntryHasEmptyListsNeverNull() {
        Entry bare = new Entry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2026-10-20"));

        EntryResponse response = mapper.toResponse(bare, NOW);

        assertThat(response.tags()).isNotNull().isEmpty();
        assertThat(response.replyIds()).isNotNull().isEmpty();
        assertThat(response.media()).isNotNull().isEmpty();
        assertThat(response.contentText()).isEmpty();
        assertThat(response.contentJson()).isNull();
        assertThat(response.title()).isNull();
    }
}
