package com.memoirist.entry;

import com.memoirist.common.NotFoundException;
import com.memoirist.common.PageResponse;
import com.memoirist.entry.dto.EntrySummaryResponse;
import com.memoirist.tag.TagRepository;
import com.memoirist.user.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's entries as list rows ({@code EntrySummary}, API_CONTRACT): today the all-entries timeline (D-25), and the
 * Opened strip, Recently deleted and On this day when tasks 3.10 to 3.12 add them. It only reads. Writing an entry is
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

    private final EntryRepository entries;
    private final TagRepository tags;
    private final CurrentUserProvider currentUser;
    private final EntryMapper mapper;
    private final Clock clock;

    public EntryListService(
            EntryRepository entries,
            TagRepository tags,
            CurrentUserProvider currentUser,
            EntryMapper mapper,
            Clock clock) {
        this.entries = entries;
        this.tags = tags;
        this.currentUser = currentUser;
        this.mapper = mapper;
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
