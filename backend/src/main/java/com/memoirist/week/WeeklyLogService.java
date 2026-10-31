package com.memoirist.week;

import com.memoirist.common.NotFoundException;
import com.memoirist.common.PageResponse;
import com.memoirist.entry.Entry;
import com.memoirist.entry.EntryRepository;
import com.memoirist.entry.EntrySeal;
import com.memoirist.era.Era;
import com.memoirist.era.EraRepository;
import com.memoirist.user.CurrentUserProvider;
import com.memoirist.week.dto.WeeklyLogResponse;
import com.memoirist.week.dto.WeeklyLogUpdateRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's weeks as the API shows them (API_CONTRACT, Feed and Weekly logs): the feed, one week's detail, its title,
 * and closing and reopening it. Weeks are never created or deleted here: the first entry in a Monday-to-Sunday span
 * creates its week ({@link WeekService}, D-08).
 *
 * <ul>
 *   <li><b>Ownership (non-negotiable 3).</b> A week is found by id <em>and</em> the current user, and another user's
 *       week is {@code 404}. Nothing here creates a row, so a header that names a user who isn't there simply owns
 *       nothing: the feed is empty and a week is {@code 404}.
 *   <li><b>The seal (D-10).</b> Every entry is built by {@code EntryMapper} through {@link WeeklyLogMapper}, so a
 *       sealed Loot Drop is a locked row. Entries in Recently deleted are hidden by {@code Entry}'s
 *       {@code @SQLRestriction} (D-30).
 *   <li><b>A fixed number of queries per page (D-22, TECH_STACK Weeks and the feed).</b> The weeks of a page, the
 *       user's Eras and the entries of all those weeks are three queries whatever the sizes involved, plus one more
 *       that counts the weeks when the page is full (Spring Data skips the count for a page with room to spare,
 *       because the total is then the offset plus the rows it got). The media of the open entries is one more query
 *       and their tags another, however many entries and photos there are; a page of nothing but sealed Loot Drops
 *       runs neither. A week's Eras are matched to it by date from the one list of the user's Eras, never fetched
 *       per week.
 * </ul>
 */
@Service
@Transactional
public class WeeklyLogService {

    private final WeeklyLogRepository weeks;
    private final EntryRepository entries;
    private final EraRepository eras;
    private final WeeklyLogMapper mapper;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public WeeklyLogService(
            WeeklyLogRepository weeks,
            EntryRepository entries,
            EraRepository eras,
            WeeklyLogMapper mapper,
            CurrentUserProvider currentUser,
            Clock clock) {
        this.weeks = weeks;
        this.entries = entries;
        this.eras = eras;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * One page of the feed: the user's weeks that have at least one entry outside Recently deleted, newest first, each
     * with its Eras and its entries (D-08, D-30). A week that is in several Eras appears once. A page past the end is
     * empty.
     */
    @Transactional(readOnly = true)
    public PageResponse<WeeklyLogResponse> feed(int page, int size) {
        UUID userId = currentUser.currentUserId();
        Page<WeeklyLog> found = weeks.findNonEmptyByUserId(userId, PageRequest.of(page, size));
        return PageResponse.of(found, build(found.getContent(), userId));
    }

    /** One week with its entries and Eras, whether or not it has any entries left. */
    @Transactional(readOnly = true)
    public WeeklyLogResponse get(UUID logId) {
        UUID userId = currentUser.currentUserId();
        return buildOne(find(logId, userId), userId);
    }

    /**
     * Replaces what the user may set on the week, which for now is its title (a {@code PUT}). The week is a managed
     * entity, so Hibernate writes the one {@code UPDATE} at commit, and none when the title is the same.
     */
    public WeeklyLogResponse update(UUID logId, WeeklyLogUpdateRequest request) {
        UUID userId = currentUser.currentUserId();
        WeeklyLog week = find(logId, userId);
        week.setTitle(request.title());
        return buildOne(week, userId);
    }

    /** Closes the week. Closing a closed week is not an error and writes nothing. */
    public WeeklyLogResponse close(UUID logId) {
        return setClosed(logId, true);
    }

    /** Reopens the week. Reopening an open week is not an error and writes nothing. */
    public WeeklyLogResponse reopen(UUID logId) {
        return setClosed(logId, false);
    }

    private WeeklyLogResponse setClosed(UUID logId, boolean closed) {
        UUID userId = currentUser.currentUserId();
        WeeklyLog week = find(logId, userId);
        // Hibernate compares the value with the one it loaded and writes nothing when they are the same, which is
        // what makes close and reopen idempotent.
        week.setClosed(closed);
        return buildOne(week, userId);
    }

    private WeeklyLog find(UUID logId, UUID userId) {
        return weeks.findByIdAndUserId(logId, userId).orElseThrow(() -> new NotFoundException("Weekly log"));
    }

    private WeeklyLogResponse buildOne(WeeklyLog week, UUID userId) {
        return build(List.of(week), userId).getFirst();
    }

    /**
     * Turns weeks into responses with the same queries however many there are: the user's Eras (once), the entries of
     * every week (once, split by week here in memory), then the media and the tags of the open entries (once each).
     * The result is in the order of {@code page}.
     */
    private List<WeeklyLogResponse> build(List<WeeklyLog> page, UUID userId) {
        if (page.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now(clock);
        List<Era> userEras = eras.findByUserIdOrderByStartDateDesc(userId);
        List<UUID> weekIds = page.stream().map(WeeklyLog::getId).toList();
        List<Entry> weekEntries = entries.findByWeeklyLogIdInAndUserIdOrderByEntryDateAscCreatedAtAsc(weekIds, userId);
        if (weekEntries.stream().anyMatch(entry -> !EntrySeal.isSealed(entry, now))) {
            // These two fill in the lazy lists of the entries just loaded (they return those same objects), so the
            // mapper below finds them ready and Hibernate never loads them one batch at a time. Both leave out sealed
            // entries by the same rule and the same `now` as the mapper. A page of only Loot Drops skips them:
            // nothing there is read.
            entries.fetchMediaOfOpenEntries(weekIds, userId, now);
            entries.fetchTagsOfOpenEntries(weekIds, userId, now);
        }
        // groupingBy keeps the order the entries arrived in within each group, so each week's list stays in feed order.
        Map<UUID, List<Entry>> entriesByWeek =
                weekEntries.stream().collect(Collectors.groupingBy(Entry::getWeeklyLogId));
        return page.stream()
                .map(week ->
                        mapper.toResponse(week, userEras, entriesByWeek.getOrDefault(week.getId(), List.of()), now))
                .toList();
    }
}
