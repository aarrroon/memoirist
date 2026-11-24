package com.memoirist.era;

import com.memoirist.common.ConflictException;
import com.memoirist.common.CreateResult;
import com.memoirist.common.NotFoundException;
import com.memoirist.common.ValidationFailedException;
import com.memoirist.entry.EntryCountByWeek;
import com.memoirist.entry.EntryRepository;
import com.memoirist.entry.EntrySeal;
import com.memoirist.era.dto.EraCreateRequest;
import com.memoirist.era.dto.EraDetailResponse;
import com.memoirist.era.dto.EraResponse;
import com.memoirist.era.dto.EraUpdateRequest;
import com.memoirist.media.Media;
import com.memoirist.media.MediaKind;
import com.memoirist.media.MediaRepository;
import com.memoirist.media.dto.MediaResponse;
import com.memoirist.user.CurrentUserProvider;
import com.memoirist.user.User;
import com.memoirist.user.UserService;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's Eras (API_CONTRACT, Eras). An Era is only its dates (D-22): creating one, changing its dates or deleting
 * it writes the {@code eras} row and nothing else, because no table says which weeks or entries it holds. The weeks
 * are found by date when an Era is read, so a new Era or new dates apply to every week, old ones included, at once.
 *
 * <p>The cover (D-36) is the one thing an Era stores that another table owns: a request may name a photo, and the
 * photo has to be an image from one of the user's own entries that is neither sealed nor in Recently deleted. What an
 * Era shows as its cover is decided again on every read by {@link EraCovers}, because the entry can be sealed,
 * deleted or restored later.
 *
 * <p>Every lookup is scoped to the current user, and an Era that is someone else's is {@code 404}, the same as one
 * that doesn't exist.
 */
@Service
@Transactional
public class EraService {

    private final EraRepository eras;
    private final WeeklyLogRepository weeks;
    private final EntryRepository entries;
    private final MediaRepository media;
    private final EraCovers covers;
    private final UserService users;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public EraService(
            EraRepository eras,
            WeeklyLogRepository weeks,
            EntryRepository entries,
            MediaRepository media,
            EraCovers covers,
            UserService users,
            CurrentUserProvider currentUser,
            Clock clock) {
        this.eras = eras;
        this.weeks = weeks;
        this.entries = entries;
        this.media = media;
        this.covers = covers;
        this.users = users;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Creates an Era, or answers with the stored one when the user already has an Era with the client's {@code id}
     * (a safe retry, D-12: the body is not applied again).
     *
     * @throws NotFoundException if the request names a user who isn't there (the first thing every create checks), or
     *     a cover photo that isn't the user's or is in Recently deleted
     * @throws ConflictException if the {@code id} belongs to another user's Era
     * @throws ValidationFailedException if the cover is a voice note or belongs to a sealed Loot Drop
     */
    public CreateResult<EraResponse> create(EraCreateRequest request) {
        User user = users.currentUser();
        Instant now = Instant.now(clock);
        UUID id = request.id();
        if (id == null) {
            id = UUID.randomUUID();
        } else {
            Optional<Era> own = eras.findByIdAndUserId(id, user.getId());
            if (own.isPresent()) {
                return CreateResult.replayed(respond(own.get(), user.getId(), now));
            }
            if (eras.existsById(id)) {
                // Someone else has it. Say only that it is taken, never whose it is or what it holds.
                throw new ConflictException("That id is already in use.");
            }
        }
        Era era = new Era(id, user.getId(), request.title(), request.startDate());
        era.setDescription(request.description());
        era.setEndDate(request.endDate());
        era.setSoundtrack(request.soundtrack());
        requireCover(request.coverMediaId(), user.getId(), now);
        era.setCoverMediaId(request.coverMediaId());
        era.setPinnedInFeed(request.pinnedInFeed());
        return CreateResult.created(respond(eras.saveAndFlush(era), user.getId(), now));
    }

    /** All of the user's Eras, the latest start first. Empty for a user with none. */
    @Transactional(readOnly = true)
    public List<EraResponse> list() {
        UUID userId = currentUser.currentUserId();
        List<Era> own = eras.findByUserIdOrderByStartDateDesc(userId).stream()
                .sorted(Era.LATEST_START_FIRST)
                .toList();
        Map<UUID, MediaResponse> shown = covers.visible(own, userId, Instant.now(clock));
        return own.stream()
                .map(era -> EraMapper.toResponse(era, shown.get(era.getId())))
                .toList();
    }

    /**
     * One Era with the weeks its dates overlap, newest first, each with the number of its entries that fall inside
     * the Era's dates (D-22). Three small queries, whatever the number of weeks or entries: the weeks, a grouped
     * count that leaves out sealed entries (D-10) and entries in Recently deleted (D-30), and the cover.
     */
    @Transactional(readOnly = true)
    public EraDetailResponse get(UUID eraId) {
        UUID userId = currentUser.currentUserId();
        Instant now = Instant.now(clock);
        Era era = find(eraId, userId);
        List<WeeklyLog> held = weeks.findOverlapping(userId, era.getStartDate(), era.getEndDate());
        Map<UUID, Long> counts =
                entries.countOpenEntriesByWeek(userId, era.getStartDate(), era.getEndDate(), now).stream()
                        .collect(Collectors.toMap(EntryCountByWeek::weeklyLogId, EntryCountByWeek::entryCount));
        return EraMapper.toDetail(era, covers.of(era, userId, now), held, counts);
    }

    /**
     * Replaces the Era's fields with the request's (a {@code PUT}). New dates apply at once to every week, because
     * the weeks are never stored (D-22). The Era is a managed entity, so Hibernate writes one {@code UPDATE} of the
     * columns that changed; the flush is explicit so that {@code updatedAt} in the response is the new one.
     *
     * <p>The cover is checked only when the request changes it. An Era whose cover is hidden now (its entry was sealed
     * or deleted since) can still be edited when the request names the same photo, and a request that leaves the cover
     * out removes it, like every other field a {@code PUT} leaves out.
     */
    public EraResponse update(UUID eraId, EraUpdateRequest request) {
        UUID userId = currentUser.currentUserId();
        Instant now = Instant.now(clock);
        Era era = find(eraId, userId);
        era.setTitle(request.title());
        era.setDescription(request.description());
        era.setStartDate(request.startDate());
        era.setEndDate(request.endDate());
        era.setSoundtrack(request.soundtrack());
        if (!Objects.equals(request.coverMediaId(), era.getCoverMediaId())) {
            requireCover(request.coverMediaId(), userId, now);
            era.setCoverMediaId(request.coverMediaId());
        }
        era.setPinnedInFeed(request.pinnedInFeed());
        return respond(eras.saveAndFlush(era), userId, now);
    }

    /** Deletes the Era and only the Era: its weeks, their entries and their other Eras stay (D-22). */
    public void delete(UUID eraId) {
        eras.delete(find(eraId, currentUser.currentUserId()));
    }

    private EraResponse respond(Era era, UUID userId, Instant now) {
        return EraMapper.toResponse(era, covers.of(era, userId, now));
    }

    // TODO(human) PRACTICE: Write requireCover: a cover must be an image from one of the user's own entries, and that
    //   entry must not be sealed. Four different failures, three different answers: a photo that is someone else's,
    //   missing or in Recently deleted (one 404 that confirms nothing), a photo of a sealed Loot Drop (400), and a
    //   voice note (400). Decide the ORDER of the checks, and what the 400 for a sealed entry may say about it.
    //   A null id means no cover, so nothing to check. Think what could happen to the photo between your check and the
    //   insert, and what the finder you call does about it.
    // Hint: D-36, D-10 and security.md (Ownership). The finder only returns the user's own media whose entry is not in
    //   Recently deleted, and takes a shared lock on the row until the transaction ends. Use EntrySeal for "sealed" and
    //   MediaKind for "voice note". If the audio check ran first, its message would tell the caller what type of file
    //   is inside a sealed entry.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    private void requireCover(UUID mediaId, UUID userId, Instant now) {
        if (mediaId == null) {
            return;
        }
        Media photo =
                media.findAndLockByIdAndEntryUserId(mediaId, userId).orElseThrow(() -> new NotFoundException("Photo"));
        if (EntrySeal.isSealed(photo.getEntry(), now)) {
            // Before the type check: a sealed entry gives away nothing, not even what kind of file it holds (D-10).
            throw new ValidationFailedException(
                    "coverMediaId", "must not be a photo from a Loot Drop that is still sealed");
        }
        if (MediaKind.of(photo) == MediaKind.VOICE_NOTE) {
            throw new ValidationFailedException("coverMediaId", "must be a photo, not a voice note");
        }
    }
    // └── END ANSWER ▲ ──

    private Era find(UUID eraId, UUID userId) {
        return eras.findByIdAndUserId(eraId, userId).orElseThrow(() -> new NotFoundException("Era"));
    }
}
