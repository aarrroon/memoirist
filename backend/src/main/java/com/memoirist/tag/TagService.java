package com.memoirist.tag;

import com.memoirist.common.ConflictException;
import com.memoirist.common.CreateResult;
import com.memoirist.common.NotFoundException;
import com.memoirist.entry.EntryCountByTag;
import com.memoirist.entry.EntryRepository;
import com.memoirist.tag.dto.TagRequest;
import com.memoirist.tag.dto.TagResponse;
import com.memoirist.user.CurrentUserProvider;
import com.memoirist.user.User;
import com.memoirist.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's tags (API_CONTRACT, Tags; D-26): their own vocabulary for entries, such as <em>Work</em> or <em>Family</em>.
 * A tag is only a name. Which entries carry it is kept by the entries ({@code entry_tags}), so renaming a tag changes no
 * entry and deleting one only removes the link: the database does that ({@code ON DELETE CASCADE}), so this service
 * never touches an entry.
 *
 * <p>Names are unique per user, ignoring case, and the database enforces it with a unique index on
 * {@code (user_id, lower(name))}. This service checks first so it can answer well, and treats the index as the final
 * word for two requests that arrive at the same moment.
 *
 * <p>Every lookup is scoped to the current user, and a tag that is someone else's is {@code 404}, the same as one that
 * doesn't exist. {@code entryCount} never counts a sealed entry (D-10): the count is made by
 * {@link EntryRepository}, whose queries leave sealed entries out themselves.
 */
@Service
@Transactional
public class TagService {

    /** The name of the unique index; a violation of any other constraint is not a name clash. */
    private static final String UNIQUE_NAME_INDEX = "uq_tags_user_name";

    private final TagRepository tags;
    private final EntryRepository entries;
    private final UserService users;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public TagService(
            TagRepository tags,
            EntryRepository entries,
            UserService users,
            CurrentUserProvider currentUser,
            Clock clock) {
        this.tags = tags;
        this.entries = entries;
        this.users = users;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Creates the tag, or answers with the one the user already has under that name, ignoring case (the stored tag, in
     * its own spelling: nothing is written).
     *
     * <p>One {@code INSERT ... ON CONFLICT DO NOTHING} decides which. It reports whether it inserted a row, so there is
     * no gap between "is the name free?" and "take it" for a second request to slip into: the loser of two simultaneous
     * creates waits for the winner to commit, inserts nothing, and then reads the winner's tag.
     *
     * @throws NotFoundException if the request names a user who isn't there (the first thing every create checks)
     */
    public CreateResult<TagResponse> create(TagRequest request) {
        User user = users.currentUser();
        UUID id = UUID.randomUUID();
        if (tags.insertIfAbsent(id, user.getId(), request.name()) == 1) {
            // A tag nothing carries yet, so there is nothing to count.
            return CreateResult.created(new TagResponse(id, request.name(), 0));
        }
        Tag existing = tags.findByUserIdAndNameIgnoreCase(user.getId(), request.name())
                .orElseThrow(() -> new IllegalStateException("The tag name was taken but the tag can't be read."));
        return CreateResult.replayed(response(existing));
    }

    /** All of the user's tags, by name (see {@link Tag#BY_NAME}). Empty for a user with none. */
    @Transactional(readOnly = true)
    public List<TagResponse> list() {
        UUID userId = currentUser.currentUserId();
        Map<UUID, Long> counts = entries.countOpenEntriesByTag(userId, Instant.now(clock)).stream()
                .collect(Collectors.toMap(EntryCountByTag::tagId, EntryCountByTag::entryCount));
        return tags.findByUserId(userId).stream()
                .sorted(Tag.BY_NAME)
                .map(tag -> TagMapper.toResponse(tag, counts.getOrDefault(tag.getId(), 0L)))
                .toList();
    }

    /**
     * Renames the tag. The name may differ from the old one only in capitals, but it may not be one of the user's
     * <em>other</em> tags (ignoring case). A name that is already the tag's own changes nothing and writes nothing.
     *
     * @throws NotFoundException if the tag doesn't exist or isn't the user's
     * @throws ConflictException if another of the user's tags has that name
     */
    public TagResponse update(UUID tagId, TagRequest request) {
        Tag tag = find(tagId);
        String name = request.name();
        if (!name.equals(tag.getName())) {
            tags.findByUserIdAndNameIgnoreCase(tag.getUserId(), name)
                    .filter(other -> !other.getId().equals(tag.getId()))
                    .ifPresent(other -> {
                        throw nameTaken();
                    });
            tag.setName(name);
            try {
                tags.saveAndFlush(tag);
            } catch (DataIntegrityViolationException e) {
                // Two renames to the same free name at the same moment both passed the check above; the unique index
                // lets the first one through and refuses the second, here.
                throw isNameClash(e) ? nameTaken() : e;
            }
        }
        return response(tag);
    }

    /**
     * Deletes the tag and only the tag. The entries that carried it keep everything they hold, sealed ones and ones in
     * Recently deleted included, and simply lose the link (the foreign key's {@code ON DELETE CASCADE} on
     * {@code entry_tags}). No entry's {@code version} or {@code updatedAt} moves.
     */
    public void delete(UUID tagId) {
        tags.delete(find(tagId));
    }

    private Tag find(UUID tagId) {
        return tags.findByIdAndUserId(tagId, currentUser.currentUserId())
                .orElseThrow(() -> new NotFoundException("Tag"));
    }

    private TagResponse response(Tag tag) {
        long count = entries.countOpenEntriesWithTag(tag.getUserId(), tag.getId(), Instant.now(clock));
        return TagMapper.toResponse(tag, count);
    }

    private static ConflictException nameTaken() {
        return new ConflictException("You already have a tag with that name.");
    }

    /** Whether the database refused the write because of the unique tag name, found anywhere in the cause chain. */
    private static boolean isNameClash(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(UNIQUE_NAME_INDEX)) {
                return true;
            }
        }
        return false;
    }
}
