package com.memoirist.era;

import com.memoirist.common.ConflictException;
import com.memoirist.common.CreateResult;
import com.memoirist.common.NotFoundException;
import com.memoirist.entry.EntryCountByWeek;
import com.memoirist.entry.EntryRepository;
import com.memoirist.era.dto.EraCreateRequest;
import com.memoirist.era.dto.EraDetailResponse;
import com.memoirist.era.dto.EraResponse;
import com.memoirist.era.dto.EraUpdateRequest;
import com.memoirist.user.CurrentUserProvider;
import com.memoirist.user.User;
import com.memoirist.user.UserService;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * <p>Every lookup is scoped to the current user, and an Era that is someone else's is {@code 404}, the same as one
 * that doesn't exist.
 */
@Service
@Transactional
public class EraService {

    private final EraRepository eras;
    private final WeeklyLogRepository weeks;
    private final EntryRepository entries;
    private final UserService users;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public EraService(
            EraRepository eras,
            WeeklyLogRepository weeks,
            EntryRepository entries,
            UserService users,
            CurrentUserProvider currentUser,
            Clock clock) {
        this.eras = eras;
        this.weeks = weeks;
        this.entries = entries;
        this.users = users;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Creates an Era, or answers with the stored one when the user already has an Era with the client's {@code id}
     * (a safe retry, D-12: the body is not applied again).
     *
     * @throws NotFoundException if the request names a user who isn't there (the first thing every create checks)
     * @throws ConflictException if the {@code id} belongs to another user's Era
     */
    public CreateResult<EraResponse> create(EraCreateRequest request) {
        User user = users.currentUser();
        UUID id = request.id();
        if (id == null) {
            id = UUID.randomUUID();
        } else {
            Optional<Era> own = eras.findByIdAndUserId(id, user.getId());
            if (own.isPresent()) {
                return CreateResult.replayed(EraMapper.toResponse(own.get()));
            }
            if (eras.existsById(id)) {
                // Someone else has it. Say only that it is taken, never whose it is or what it holds.
                throw new ConflictException("That id is already in use.");
            }
        }
        Era era = new Era(id, user.getId(), request.title(), request.startDate());
        era.setDescription(request.description());
        era.setEndDate(request.endDate());
        era.setPinnedInFeed(request.pinnedInFeed());
        return CreateResult.created(EraMapper.toResponse(eras.saveAndFlush(era)));
    }

    /** All of the user's Eras, the latest start first. Empty for a user with none. */
    @Transactional(readOnly = true)
    public List<EraResponse> list() {
        return eras.findByUserIdOrderByStartDateDesc(currentUser.currentUserId()).stream()
                .sorted(Era.LATEST_START_FIRST)
                .map(EraMapper::toResponse)
                .toList();
    }

    /**
     * One Era with the weeks its dates overlap, newest first, each with the number of its entries that fall inside
     * the Era's dates (D-22). Two small queries, whatever the number of weeks or entries: the weeks, then a grouped
     * count that leaves out sealed entries (D-10) and entries in Recently deleted (D-30).
     */
    @Transactional(readOnly = true)
    public EraDetailResponse get(UUID eraId) {
        UUID userId = currentUser.currentUserId();
        Era era = find(eraId, userId);
        List<WeeklyLog> held = weeks.findOverlapping(userId, era.getStartDate(), era.getEndDate());
        Map<UUID, Long> counts =
                entries
                        .countOpenEntriesByWeek(userId, era.getStartDate(), era.getEndDate(), Instant.now(clock))
                        .stream()
                        .collect(Collectors.toMap(EntryCountByWeek::weeklyLogId, EntryCountByWeek::entryCount));
        return EraMapper.toDetail(era, held, counts);
    }

    /**
     * Replaces the Era's fields with the request's (a {@code PUT}). New dates apply at once to every week, because
     * the weeks are never stored (D-22). The Era is a managed entity, so Hibernate writes one {@code UPDATE} of the
     * columns that changed; the flush is explicit so that {@code updatedAt} in the response is the new one.
     */
    public EraResponse update(UUID eraId, EraUpdateRequest request) {
        Era era = find(eraId, currentUser.currentUserId());
        era.setTitle(request.title());
        era.setDescription(request.description());
        era.setStartDate(request.startDate());
        era.setEndDate(request.endDate());
        era.setPinnedInFeed(request.pinnedInFeed());
        return EraMapper.toResponse(eras.saveAndFlush(era));
    }

    /** Deletes the Era and only the Era: its weeks, their entries and their other Eras stay (D-22). */
    public void delete(UUID eraId) {
        eras.delete(find(eraId, currentUser.currentUserId()));
    }

    private Era find(UUID eraId, UUID userId) {
        return eras.findByIdAndUserId(eraId, userId).orElseThrow(() -> new NotFoundException("Era"));
    }
}
