package com.memoirist.entry;

import com.memoirist.entry.dto.EntryResponse;
import com.memoirist.entry.dto.EntryTagResponse;
import com.memoirist.media.MediaMapper;
import com.memoirist.tag.Tag;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * <b>The only place an {@link Entry} becomes a response.</b> Create, read, update, seal, the feed (3.7), week detail,
 * share links (Phase 2) and group views (Phase 3) all call {@link #toResponse}, so the rule that keeps a sealed Loot
 * Drop secret (D-10, non-negotiable 4) is written once. {@code EntryMapperIsTheOnlyPathTest} fails if another source
 * file builds an {@link EntryResponse}.
 *
 * <p>How the seal is kept:
 * <ul>
 *   <li>A sealed entry is built by {@link #sealed}, which lists the fields that may be shown (the id, the week, the
 *       memory date, the timestamps, the unlock date, {@code dateUnknown} and the version) and fills in nothing else.
 *       It is written as an allow-list, not as "copy everything and blank some fields": a field added to
 *       {@code EntryResponse} later shows up as a compile error in <em>both</em> branches, and the author has to decide
 *       whether it is private, instead of it being copied by default.
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

    private static final Comparator<Tag> BY_NAME_THEN_ID =
            Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Tag::getId);

    private final MediaMapper mediaMapper;

    public EntryMapper(MediaMapper mediaMapper) {
        this.mediaMapper = mediaMapper;
    }

    /**
     * The entry as the client may see it at {@code now}: complete, or, while its unlock date is still ahead,
     * with everything private left out.
     */
    public EntryResponse toResponse(Entry entry, Instant now) {
        return EntrySeal.isSealed(entry, now) ? sealed(entry) : open(entry);
    }

    private EntryResponse open(Entry entry) {
        return new EntryResponse(
                entry.getId(),
                entry.getWeeklyLogId(),
                entry.getEntryDate(),
                entry.getTemplateId(),
                entry.getTitle(),
                entry.getContentJson(),
                entry.getContentText(),
                entry.getTags().stream()
                        .sorted(BY_NAME_THEN_ID)
                        .map(tag -> new EntryTagResponse(tag.getId(), tag.getName(), TAG_SOURCE_USER))
                        .toList(),
                entry.getSoundtrack(),
                entry.getReplyToEntryId(),
                // Replies are recorded from task 3.13 on; until then no entry has one.
                List.of(),
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
