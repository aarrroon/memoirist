package com.memoirist.era;

import com.memoirist.entry.EntrySeal;
import com.memoirist.media.Media;
import com.memoirist.media.MediaMapper;
import com.memoirist.media.MediaRepository;
import com.memoirist.media.dto.MediaResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Which cover an Era shows right now (D-36). An Era only stores the id of its cover photo; whether the photo may be
 * seen depends on its entry, which can change at any moment, so it is decided every time an Era is read:
 * <ul>
 *   <li>an entry in Recently deleted hides it (D-30): the finder joins the entry, and the entry's
 *       {@code @SQLRestriction} drops the row;
 *   <li>a sealed Loot Drop hides it (D-10), decided by {@link EntrySeal} like every other place;
 *   <li>otherwise it is presigned by {@link MediaMapper}, the one class that signs a file URL.
 * </ul>
 * Nothing is deleted or cleared while the cover is hidden: when the entry is restored, or opens, the cover is back.
 *
 * <p>It reads all the covers of a list of Eras in one query, so {@code GET /eras} costs the same for 1 Era or 100.
 * Call it inside the service's transaction.
 */
@Component
class EraCovers {

    private final MediaRepository media;
    private final MediaMapper mediaMapper;

    EraCovers(MediaRepository media, MediaMapper mediaMapper) {
        this.media = media;
        this.mediaMapper = mediaMapper;
    }

    /** The cover of one Era, or {@code null} when it has none or may not be shown. */
    MediaResponse of(Era era, UUID userId, Instant now) {
        return visible(List.of(era), userId, now).get(era.getId());
    }

    // TODO(human) PRACTICE: Write visible(): from a list of Eras, answer a map from Era id to the cover to show, in ONE
    //   query however many Eras there are. An Era with no cover, a cover whose entry is sealed or in Recently
    //   deleted, or a cover that is not found, is simply not in the map. Nothing here may sign a URL for a sealed
    //   entry's photo, and the lookup must be scoped to the user.
    // Hint: D-36, D-10 and non-negotiable 4. Two Eras can use the same photo, so build the map of media by id first and
    //   then look each Era up in it. Ask EntrySeal, don't re-write "unlock date is ahead". Which class may sign a URL?
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    Map<UUID, MediaResponse> visible(Collection<Era> eras, UUID userId, Instant now) {
        Set<UUID> wanted =
                eras.stream().map(Era::getCoverMediaId).filter(id -> id != null).collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Media> found = media.findAllWithEntryByIdInAndUserId(wanted, userId).stream()
                .filter(item -> !EntrySeal.isSealed(item.getEntry(), now))
                .collect(Collectors.toMap(Media::getId, item -> item));
        Map<UUID, MediaResponse> covers = new HashMap<>();
        for (Era era : eras) {
            Media cover = found.get(era.getCoverMediaId());
            if (cover != null) {
                covers.put(era.getId(), mediaMapper.toResponse(cover.getEntry().getId(), cover));
            }
        }
        return covers;
    }
    // └── END ANSWER ▲ ──
}
