package com.memoirist.entry;

import com.memoirist.common.NotFoundException;
import com.memoirist.common.PageResponse;
import com.memoirist.entry.dto.EntrySummaryResponse;
import com.memoirist.tag.TagRepository;
import com.memoirist.user.CurrentUserProvider;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's entries as list rows ({@code EntrySummary}, API_CONTRACT): the all-entries timeline (D-25), the Opened
 * strip (task 3.10), Recently deleted (task 3.11) and On this day (task 3.12). It only reads. Writing an entry is
 * {@link EntryService}'s job, so the rules about changing one stay in one class and the read paths, which are mostly
 * about queries and how many of them run, stay in another.
 *
 * <ul>
 *   <li><b>Ownership (non-negotiable 3).</b> Every query is scoped to the current user, and a tag in the filter must be
 *       the user's own: someone else's, and one that doesn't exist, are the same {@code 404}. A header that names a
 *       user who isn't there owns nothing, so the list is empty.
 *   <li><b>The seal (D-10).</b> The unfiltered list shows a sealed Loot Drop as a locked row, built by
 *       {@link EntryMapper#toSummary}. The tag filter leaves sealed entries out <em>in the query</em>, so they are not
 *       listed and not counted. Entries in Recently deleted are hidden by {@code Entry}'s {@code @SQLRestriction}
 *       (D-30).
 *   <li><b>A fixed number of queries per page.</b> The page of entries, the media of the open ones and their tags are
 *       three queries however many entries and photos there are, plus one more that counts the entries when the page is
 *       full (Spring Data skips the count for a page with room to spare), plus one to check the tag when there is a
 *       filter. A page with nothing open on it runs neither the media nor the tags query.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class EntryListService {

    /** How many Loot Drops the Opened strip shows (API_CONTRACT, Entries). */
    static final int OPENED_STRIP_SIZE = 20;

    /** How many entries the On this day strip shows (API_CONTRACT, Entries). */
    static final int ON_THIS_DAY_SIZE = 20;

    private final EntryRepository entries;
    private final TagRepository tags;
    private final UserRepository users;
    private final CurrentUserProvider currentUser;
    private final EntryMapper mapper;
    private final EntityManager entityManager;
    private final Clock clock;

    public EntryListService(
            EntryRepository entries,
            TagRepository tags,
            UserRepository users,
            CurrentUserProvider currentUser,
            EntryMapper mapper,
            EntityManager entityManager,
            Clock clock) {
        this.entries = entries;
        this.tags = tags;
        this.users = users;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * One page of every entry in the journal, newest memory date first and then newest written (D-25), or, with a
     * {@code tagId}, of the open entries that carry that tag. A page past the end is empty.
     *
     * @param page the zero-based page number; the controller has checked that it isn't negative
     * @param size the page size; the controller has checked it against the contract's limits
     * @param tagId the tag to filter by, or {@code null} for no filter
     * @throws NotFoundException if {@code tagId} is not one of the user's tags
     */
    public PageResponse<EntrySummaryResponse> list(int page, int size, UUID tagId) {
        UUID userId = currentUser.currentUserId();
        Instant now = Instant.now(clock);
        PageRequest pageable = PageRequest.of(page, size);

        Page<Entry> found;
        if (tagId == null) {
            found = entries.findTimelinePage(userId, pageable);
        } else {
            // Checked first so a tag that isn't the user's is 404, as it is on an entry's tagIds, and not an empty
            // page that would look the same as a tag nobody has used yet.
            tags.findByIdAndUserId(tagId, userId).orElseThrow(() -> new NotFoundException("Tag"));
            found = entries.findOpenTimelinePageByTag(userId, tagId, now, pageable);
        }
        return PageResponse.of(found, summaries(found.getContent(), userId, now));
    }

    /**
     * The Opened strip (D-10): the user's Loot Drops that have unlocked and that they haven't marked seen, the one that
     * opened most recently first, at most {@value #OPENED_STRIP_SIZE}. A Loot Drop that is sealed (again) isn't here,
     * and neither is one in Recently deleted; marking one seen with {@code EntryService.markSeen} takes it out of the
     * strip, and sealing it again puts it back once it has opened again.
     *
     * <p>Every entry the query returns is open at {@code now}, so each row is whole (title, preview, counts, tags).
     * The query and the mapper use the same {@code now}, and an entry that opens in between is simply not in this
     * answer yet. The cost is the strip's one query plus the media and the tags of its rows, three statements for any
     * number of Loot Drops, the same as a page of the timeline without its count.
     */
    public List<EntrySummaryResponse> opened() {
        UUID userId = currentUser.currentUserId();
        Instant now = Instant.now(clock);
        List<Entry> found = entries.findOpenedUnseen(userId, now, Limit.of(OPENED_STRIP_SIZE));
        return summaries(found, userId, now);
    }

    /**
     * The On this day strip (D-32): what the user wrote on this day and month in earlier years, the newest year first,
     * at most {@value #ON_THIS_DAY_SIZE}. Entries still sealed and entries in Recently deleted are not here (not even as
     * a locked row, so nothing hints that a Loot Drop is waiting); a Loot Drop that has opened is an ordinary entry
     * again and is.
     *
     * <p><b>"Today" is the user's today.</b> The clock is read once, and that one instant gives both the seal check
     * ({@code now}) and the date, taken in the user's own zone (D-07), never the server's. Memory dates are calendar
     * days in that zone already, so they are matched as they are. The day and month rule, including the 28 and 29
     * February one, is {@link OnThisDay}.
     *
     * <p>The zone is on the user, so this looks the user up. A header that names a user who isn't there owns no
     * entries: the answer is an empty strip, as for the other lists (API_CONTRACT, Who is the current user?), not the
     * {@code 404} that {@code UserService.currentUser()} gives a create. The cost is that lookup, the strip's one query
     * and then the media and tags of its rows: four statements for any number of entries, two when the strip is empty.
     */
    public List<EntrySummaryResponse> onThisDay() {
        UUID userId = currentUser.currentUserId();
        Optional<User> user = users.findById(userId);
        if (user.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now(clock);
        LocalDate today = now.atZone(ZoneId.of(user.get().getTimezone())).toLocalDate();
        OnThisDay.Lookup lookup = OnThisDay.lookupFor(today);
        List<Entry> found = entries.findOnThisDay(
                userId, lookup.month().getValue(), lookup.days(), lookup.before(), now, Limit.of(ON_THIS_DAY_SIZE));
        return summaries(found, userId, now);
    }

    /**
     * The user's Recently deleted list (D-30), the entry deleted most recently first, with its {@code deletedAt}. A
     * sealed Loot Drop is still a locked row: only its memory date, unlock date and when it was deleted show (D-10),
     * and one that has opened since is whole again. A page past the end is empty.
     *
     * <p>The only list that reads entries through a native query, because {@code @SQLRestriction} on {@code Entry} hides
     * the very rows it lists from every JPQL query, including the media and tags queries {@link #summaries} uses. So
     * the media counts and the tags of an open row are filled in by Hibernate itself, when the mapper reads them, in
     * its batches of 50 ({@code @BatchSize}): one statement for the media and one for the tags of the whole page.
     *
     * <p><b>Loot Drops are detached first.</b> A batch loads the lists of <em>every</em> entry of the page that has not
     * loaded them yet, not only the one being read, and the mapper never reads a sealed entry's lists. Left attached, a
     * Loot Drop's media and tags would be fetched from the database along with the open entries' and then thrown
     * away. Nothing leaks, but it breaks the rule the other lists keep (D-10): a sealed entry's children are never
     * read. A detached entry is no longer in the session, so the batch skips it, and the mapper's sealed branch needs
     * nothing but the entry's own columns. A test counts the lists Hibernate loads.
     */
    public PageResponse<EntrySummaryResponse> deleted(int page, int size) {
        UUID userId = currentUser.currentUserId();
        Instant now = Instant.now(clock);
        Page<Entry> found = entries.findDeletedPageByUserId(userId, PageRequest.of(page, size));
        found.getContent().stream()
                .filter(entry -> EntrySeal.isSealed(entry, now))
                .forEach(entityManager::detach);
        List<EntrySummaryResponse> rows = found.getContent().stream()
                .map(entry -> mapper.toSummary(entry, now))
                .toList();
        return PageResponse.of(found, rows);
    }

    /**
     * Turns the entries of a page into rows with the same queries however many there are: the media of the open
     * entries (once) and their tags (once). They fill in the lazy lists of the entries just loaded (the queries return
     * those same objects), so {@link EntryMapper#toSummary} finds them ready and Hibernate never loads them one batch at
     * a time. Only the ids of open entries are asked for, by the same {@link EntrySeal} the mapper uses, so a sealed
     * entry's media and tags are never read, and a page of nothing but Loot Drops runs neither query.
     */
    private List<EntrySummaryResponse> summaries(List<Entry> page, UUID userId, Instant now) {
        List<UUID> openIds = page.stream()
                .filter(entry -> !EntrySeal.isSealed(entry, now))
                .map(Entry::getId)
                .toList();
        if (!openIds.isEmpty()) {
            entries.fetchMediaOfOpenEntriesByIds(openIds, userId, now);
            entries.fetchTagsOfOpenEntriesByIds(openIds, userId, now);
        }
        return page.stream().map(entry -> mapper.toSummary(entry, now)).toList();
    }
}
