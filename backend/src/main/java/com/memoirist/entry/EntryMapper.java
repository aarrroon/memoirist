package com.memoirist.entry;

import com.memoirist.entry.dto.EntryResponse;
import com.memoirist.entry.dto.EntrySummaryResponse;
import com.memoirist.entry.dto.EntryTagResponse;
import com.memoirist.media.MediaMapper;
import com.memoirist.media.MediaSummary;
import com.memoirist.tag.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * <b>The only place an {@link Entry} becomes a response.</b> Create, read, update, seal, the feed (3.7), week detail,
 * share links (Phase 2) and group views (Phase 3) all call {@link #toResponse}, and every list row (the all-entries
 * timeline, the Opened strip, Recently deleted and On this day) calls {@link #toSummary}, so the rule that
 * keeps a sealed Loot Drop secret (D-10, non-negotiable 4) is written once. {@code EntryMapperIsTheOnlyPathTest} fails
 * if another source file builds an {@link EntryResponse} or an {@link EntrySummaryResponse}.
 *
 * <p>How the seal is kept:
 * <ul>
 *   <li>A sealed entry is built by {@link #sealed} (or {@link #sealedSummary}), which lists the fields that may be
 *       shown (the id, the week, the memory date, the timestamps, the unlock date, {@code dateUnknown} and, for the
 *       full entry, the version) and fills in nothing else. It is written as an allow-list, not as "copy everything and
 *       blank some fields": a field added to a response record later shows up as a compile error in <em>both</em>
 *       branches, and the author has to decide whether it is private, instead of it being copied by default.
 *   <li>It reads nothing private from the entity. In particular it never touches {@code getMedia()} or
 *       {@code getTags()}, so no query runs for a sealed entry and no URL is signed for its photos.
 * </ul>
 *
 * <p>It reads lazy collections, so call it inside the service's transaction (open-in-view is off).
 */
@Component
public class EntryMapper {

    /** Until Phase 4 adds {@code AI}, every tag on an entry was put there by the user (D-26). */
    private static final String TAG_SOURCE_USER = "USER";

    private final MediaMapper mediaMapper;

    public EntryMapper(MediaMapper mediaMapper) {
        this.mediaMapper = mediaMapper;
    }

    /**
     * The entry as the client may see it at {@code now}: complete, or, while its unlock date is still ahead,
     * with everything private left out.
     */
    public EntryResponse toResponse(Entry entry, Instant now) {
        return EntrySeal.isSealed(entry, now) ? sealed(entry) : open(entry, now);
    }

    private EntryResponse open(Entry entry, Instant now) {
        return new EntryResponse(
                entry.getId(),
                entry.getWeeklyLogId(),
                entry.getEntryDate(),
                entry.getTemplateId(),
                entry.getTitle(),
                entry.getContentJson(),
                entry.getContentText(),
                tagsOf(entry),
                entry.getSoundtrack(),
                entry.getReplyToEntryId(),
                replyIdsOf(entry, now),
                entry.getCreatedAt(),
                entry.getUpdatedAt(),
                entry.getUnlockDate(),
                false,
                entry.isDateUnknown(),
                entry.getVersion(),
                entry.getMedia().stream()
                        .map(media -> mediaMapper.toResponse(entry.getId(), media))
                        .toList());
    }

    /**
     * The entry as a list row at {@code now}: complete (without the caption), or, while its unlock date is still ahead,
     * with everything private left out. The same seal rule as {@link #toResponse}, from the same {@link EntrySeal}.
     *
     * <p>A row shows the entry's media counts and tags, so the caller loads those lists for a whole page up front
     * (see {@code EntryListService}); if it forgot, they would load in Hibernate's batches of 50, the same answer a
     * little slower.
     */
    public EntrySummaryResponse toSummary(Entry entry, Instant now) {
        return EntrySeal.isSealed(entry, now) ? sealedSummary(entry) : openSummary(entry);
    }

    private EntrySummaryResponse openSummary(Entry entry) {
        MediaSummary media = MediaSummary.of(entry.getMedia());
        return new EntrySummaryResponse(
                entry.getId(),
                entry.getWeeklyLogId(),
                entry.getEntryDate(),
                entry.getTitle(),
                entry.getPreviewLine(),
                media.photoCount(),
                media.voiceNoteCount(),
                media.firstKind(),
                tagsOf(entry),
                entry.getCreatedAt(),
                entry.getUnlockDate(),
                false,
                entry.isDateUnknown(),
                entry.getDeletedAt());
    }

    /** A Loot Drop that hasn't opened, as a list row. As in {@link #sealed}, what is shown is named. */
    private EntrySummaryResponse sealedSummary(Entry entry) {
        return new EntrySummaryResponse(
                entry.getId(),
                entry.getWeeklyLogId(),
                entry.getEntryDate(),
                null, // title
                null, // previewLine
                null, // photoCount
                null, // voiceNoteCount
                null, // firstMediaKind
                List.of(), // tags
                entry.getCreatedAt(),
                entry.getUnlockDate(),
                true,
                entry.isDateUnknown(),
                entry.getDeletedAt());
    }

    /** The entry's tags in the order of {@code GET /tags}, so a client sees the same sequence everywhere. */
    private static List<EntryTagResponse> tagsOf(Entry entry) {
        return entry.getTags().stream()
                .sorted(Tag.BY_NAME)
                .map(tag -> new EntryTagResponse(tag.getId(), tag.getName(), TAG_SOURCE_USER))
                .toList();
    }

    /**
     * The ids of the replies to an opened Loot Drop, oldest first (D-33). Three rules, each from the seal (D-10):
     * <ul>
     *   <li>An entry that was never sealed has no replies (a reply can only answer an opened Loot Drop), so it is not
     *       even asked, and no query runs for the ordinary entries of a feed.
     *   <li>A reply that is itself still sealed is left out until it opens, as every other count and list leaves a
     *       sealed entry out. It is decided by {@link EntrySeal}, like everywhere else.
     *   <li>A reply in Recently deleted never reaches this list ({@code @SQLRestriction} on the collection).
     * </ul>
     */
    private static List<UUID> replyIdsOf(Entry entry, Instant now) {
        if (entry.getUnlockDate() == null) {
            return List.of();
        }
        return entry.getReplies().stream()
                .filter(reply -> !EntrySeal.isSealed(reply, now))
                .map(Entry::getId)
                .toList();
    }

    /** A Loot Drop that hasn't opened. Each line is a decision: what is shown is named, everything else is empty. */
    private EntryResponse sealed(Entry entry) {
        return new EntryResponse(
                entry.getId(),
                entry.getWeeklyLogId(),
                entry.getEntryDate(),
                null, // templateId
                null, // title
                null, // contentJson
                null, // contentText
                List.of(), // tags
                null, // soundtrack
                null, // replyToEntryId
                List.of(), // replyIds
                entry.getCreatedAt(),
                entry.getUpdatedAt(),
                entry.getUnlockDate(),
                true,
                entry.isDateUnknown(),
                entry.getVersion(),
                List.of()); // media
    }
}
