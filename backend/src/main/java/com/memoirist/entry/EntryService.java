package com.memoirist.entry;

import com.memoirist.common.ConflictException;
import com.memoirist.common.CreateResult;
import com.memoirist.common.EntrySealedException;
import com.memoirist.common.EntryStaleException;
import com.memoirist.common.NotFoundException;
import com.memoirist.common.ValidationFailedException;
import com.memoirist.entry.dto.EntryCreateRequest;
import com.memoirist.entry.dto.EntryResponse;
import com.memoirist.entry.dto.EntrySealRequest;
import com.memoirist.entry.dto.EntryUpdateRequest;
import com.memoirist.tag.Tag;
import com.memoirist.tag.TagRepository;
import com.memoirist.template.TemplateRepository;
import com.memoirist.user.CurrentUserProvider;
import com.memoirist.user.User;
import com.memoirist.user.UserService;
import com.memoirist.week.WeekService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * The user's entries (API_CONTRACT, Entries): create, read, edit, delete, seal and mark seen. This is where the rules that
 * protect a journal live:
 *
 * <ul>
 *   <li><b>Ownership (non-negotiable 3).</b> Every lookup is by id <em>and</em> the current user, and an entry that is
 *       someone else's is {@code 404}, the same as one that doesn't exist. An entry in Recently deleted is hidden from
 *       these lookups by {@code @SQLRestriction} on {@link Entry}, so it is {@code 404} too (D-30).
 *   <li><b>The seal (D-10).</b> A sealed entry can't be edited ({@code 409 entry-sealed}) and its unlock date can only
 *       move later. It can still be deleted. Every response is built by {@link EntryMapper}, which hides what is private.
 *   <li><b>Stale edits (D-23).</b> A {@code PUT} carries the version the client read. If the entry has moved on, the
 *       answer is {@code 409 entry-stale} and nothing is written.
 *   <li><b>Time (D-07, D-09).</b> "Today", and so the latest allowed memory date, is the user's today, from the
 *       injected {@link Clock}.
 * </ul>
 *
 * <p>Tags are set here, from the {@code tagIds} of a create or an update (D-26); {@code TagService} makes and renames
 * the tags themselves. Soundtracks and reply targets are set by tasks 3.14 and 3.13, and the entry's media by
 * {@code MediaService} (task 3.6). This class doesn't change those: the mapper only reads them, and {@code delete}
 * hands an empty entry to {@link EntryErasure}.
 */
@Service
@Transactional
public class EntryService {

    /** How far ahead a Loot Drop may be sealed (API_CONTRACT, Entries). */
    private static final int MAX_SEAL_YEARS = 100;

    private final EntryRepository entries;
    private final TemplateRepository templates;
    private final TagRepository tags;
    private final WeekService weeks;
    private final UserService users;
    private final CurrentUserProvider currentUser;
    private final EntryMapper mapper;
    private final EntryErasure erasure;
    private final Clock clock;

    public EntryService(
            EntryRepository entries,
            TemplateRepository templates,
            TagRepository tags,
            WeekService weeks,
            UserService users,
            CurrentUserProvider currentUser,
            EntryMapper mapper,
            EntryErasure erasure,
            Clock clock) {
        this.entries = entries;
        this.templates = templates;
        this.tags = tags;
        this.weeks = weeks;
        this.users = users;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.erasure = erasure;
        this.clock = clock;
    }

    /**
     * Creates an entry in the week of its memory date (the week is created if it is the first entry in it), or answers
     * with the stored one when the user already has an entry with the client's {@code id} (a safe retry, D-12: the body
     * is not applied again).
     *
     * @throws NotFoundException if the request names a user who isn't there (the first thing every create checks), or
     *     a template that isn't the user's
     * @throws ConflictException if the {@code id} belongs to another user's entry, or to one of the user's own entries
     *     in Recently deleted (restore that one instead, D-30)
     * @throws ValidationFailedException if the date is after today or before 1900-01-01, or the content breaks a rule
     * @throws com.memoirist.common.PayloadTooLargeException if the content is over 200 KB
     */
    public CreateResult<EntryResponse> create(EntryCreateRequest request) {
        User user = users.currentUser();
        Instant now = Instant.now(clock);
        UUID id = request.id();
        if (id == null) {
            id = UUID.randomUUID();
        } else {
            Optional<Entry> own = entries.findByIdAndUserId(id, user.getId());
            if (own.isPresent()) {
                return CreateResult.replayed(mapper.toResponse(own.get(), now));
            }
            if (entries.idIsTaken(id)) {
                // Someone else has it, or it waits in the user's Recently deleted. Say only that it is taken.
                throw new ConflictException("That id is already in use.");
            }
        }

        LocalDate today = today(user);
        LocalDate date = request.entryDate() == null ? today : request.entryDate();
        checkMemoryDate(date, today);
        checkContent(request.contentJson());
        checkTemplate(request.templateId(), user.getId());
        List<Tag> tagsToLink = requireOwnedTags(request.tagIds(), user.getId());

        Entry entry = new Entry(
                id, user.getId(), weeks.findOrCreate(user.getId(), date).getId(), date);
        entry.setTemplateId(request.templateId());
        entry.setTitle(request.title());
        setContent(entry, request.contentJson());
        entry.setDateUnknown(Boolean.TRUE.equals(request.dateUnknown()));
        entry.replaceTags(tagsToLink);
        return CreateResult.created(mapper.toResponse(entries.saveAndFlush(entry), now));
    }

    /** One entry, redacted if it is a sealed Loot Drop. */
    @Transactional(readOnly = true)
    public EntryResponse get(UUID entryId) {
        return mapper.toResponse(find(entryId), Instant.now(clock));
    }

    /**
     * Replaces the entry's date, template, title, caption and tags with the request's (a {@code PUT}). A new date moves
     * the entry to the week that contains it and clears {@code dateUnknown} (D-34); an unchanged date leaves both alone.
     *
     * <p>The checks run in this order, and the first to fail answers: the entry is the user's ({@code 404}), it isn't
     * sealed ({@code 409 entry-sealed}, before the tags are looked at, so a sealed entry can't be used to find out
     * whether a tag id exists), the client has seen its latest version ({@code 409 entry-stale}), then the new values
     * are valid ({@code 400}, or {@code 404} for a template or tag that isn't theirs). Nothing is written until all
     * have passed.
     */
    public EntryResponse update(UUID entryId, EntryUpdateRequest request) {
        Entry entry = find(entryId);
        Instant now = Instant.now(clock);
        if (EntrySeal.isSealed(entry, now)) {
            throw new EntrySealedException("This entry is sealed and can't be changed until it opens.");
        }
        requireCurrentVersion(entry, request.version());

        UUID userId = entry.getUserId();
        LocalDate date = request.entryDate();
        boolean dateChanged = !date.equals(entry.getEntryDate());
        if (dateChanged) {
            // Only a date the user is changing is held to today: an entry whose date has since become "in the future"
            // (the user moved their time zone) can still be edited without touching its date.
            checkMemoryDate(date, today(users.currentUser()));
        }
        checkContent(request.contentJson());
        if (!Objects.equals(request.templateId(), entry.getTemplateId())) {
            checkTemplate(request.templateId(), userId);
        }
        List<Tag> newTags = requireOwnedTags(request.tagIds(), userId);

        // Find the new week BEFORE touching the entry. Finding it runs a native INSERT, and Hibernate flushes pending
        // changes before a native query, so a setter called first would be written by its own UPDATE and the
        // saveAndFlush below would write a second one: one edit would raise the version by two (a test found this).
        UUID newWeekId = dateChanged ? weeks.findOrCreate(userId, date).getId() : entry.getWeeklyLogId();

        if (dateChanged) {
            entry.setEntryDate(date);
            entry.setWeeklyLogId(newWeekId);
            entry.setDateUnknown(false);
        }
        entry.setTemplateId(request.templateId());
        entry.setTitle(request.title());
        setContent(entry, request.contentJson());
        // The same set leaves the collection untouched; a different one makes Hibernate insert and delete just the
        // changed entry_tags rows and raise the entry's version, because the entry owns the collection.
        entry.replaceTags(newTags);
        // Hibernate adds "AND version = ?" to the UPDATE. If another request commits between the check above and this
        // flush, no row matches and the flush throws ObjectOptimisticLockingFailureException, which is also 409
        // entry-stale. An update that changes nothing writes nothing and leaves the version where it was.
        entries.saveAndFlush(entry);
        return mapper.toResponse(entry, now);
    }

    /**
     * Deletes the entry. It goes to Recently deleted for 30 days (D-30): hidden everywhere at once, but its media files
     * stay until it is erased (task 3.11). Allowed when the entry is sealed, which stays sealed in there.
     *
     * <p>An entry with no title, no caption and no media has nothing to bring back, so it is erased at once instead,
     * through {@link EntryErasure}, which is also the path that deletes the stored files of an entry that has media.
     *
     * <p>The entry's row is locked first. A photo upload takes the same lock before it adds its row, so the two take
     * turns: either the photo is in before this looks, and the entry isn't empty, or this wins and the upload finds no
     * entry. Without the lock, an upload that finished in between would have its media row removed by the cascade
     * and its file left in storage with nothing pointing at it.
     */
    public void delete(UUID entryId) {
        Entry entry = entries.findForUpdateByIdAndUserId(entryId, currentUser.currentUserId())
                .orElseThrow(() -> new NotFoundException("Entry"));
        if (isEmpty(entry)) {
            erasure.erase(entry);
        } else {
            entry.setDeletedAt(Instant.now(clock));
            entries.saveAndFlush(entry);
        }
    }

    /**
     * Seals the entry as a Loot Drop that opens at {@code unlockDate}, or moves the date of one that is already
     * sealed. Sealing also clears the "seen" mark, so an opened Loot Drop that is sealed again shows in the Opened
     * strip once more when it opens (D-10). The answer is the sealed entry, so it is already redacted.
     *
     * @throws ValidationFailedException if the date isn't in the future, or is more than 100 years away
     * @throws EntrySealedException if the entry is sealed and the date isn't later than its current unlock date
     */
    public EntryResponse seal(UUID entryId, EntrySealRequest request) {
        Entry entry = find(entryId);
        Instant now = Instant.now(clock);
        // The database keeps microseconds. Cut the request to match, so the answer shows what is stored.
        Instant unlockDate = request.unlockDate().truncatedTo(ChronoUnit.MICROS);
        if (!unlockDate.isAfter(now)) {
            throw new ValidationFailedException("unlockDate", "must be in the future");
        }
        if (unlockDate.isAfter(
                now.atOffset(ZoneOffset.UTC).plusYears(MAX_SEAL_YEARS).toInstant())) {
            throw new ValidationFailedException("unlockDate", "must be at most " + MAX_SEAL_YEARS + " years ahead");
        }
        if (EntrySeal.isSealed(entry, now) && !unlockDate.isAfter(entry.getUnlockDate())) {
            throw new EntrySealedException("The unlock date of a sealed entry can only move later.");
        }
        entry.setUnlockDate(unlockDate);
        entry.setUnlockSeenAt(null);
        entries.saveAndFlush(entry);
        return mapper.toResponse(entry, now);
    }

    /**
     * Records that the user has seen an opened Loot Drop, which takes it out of the Opened strip (D-10). Safe to
     * repeat: the first call writes the time and every later one changes nothing, so the first seen time stays.
     *
     * <p>The checks, in order: the entry is the user's and not in Recently deleted ({@code 404}), then it is not sealed
     * ({@code 409 entry-sealed}: it has not opened, so there is nothing to have seen). An entry that was never sealed
     * is not a Loot Drop and has nothing to mark, so it answers like a repeat and nothing is written.
     *
     * <p>Marking is one conditional {@code UPDATE} ({@link EntryRepository#markSeen}), not a setter and a save, so it
     * does not raise {@code version} or move {@code updatedAt}: a view mark is not an edit (D-23), and the client that
     * marks an entry seen and then saves the caption it read earlier must not be told its copy is stale.
     */
    public void markSeen(UUID entryId) {
        Entry entry = find(entryId);
        Instant now = Instant.now(clock);
        if (EntrySeal.isSealed(entry, now)) {
            throw new EntrySealedException("This entry is sealed, so it has not opened yet.");
        }
        entries.markSeen(entry.getId(), entry.getUserId(), now);
    }

    private Entry find(UUID entryId) {
        return entries.findByIdAndUserId(entryId, currentUser.currentUserId())
                .orElseThrow(() -> new NotFoundException("Entry"));
    }

    // TODO(human) PRACTICE: Write requireCurrentVersion: refuse a PUT that was based on an older copy of the entry.
    //   The client sends the version it last read; the entry holds the version stored now. Decide what to do when
    //   they differ in each direction, and decide whether the client's number is ever copied onto the entity.
    // Hint: D-23 and .claude/rules/backend.md (Persistence). Hibernate already refuses a save of an old copy, but
    //   only for an entity it loaded in the same request; this method is the check that covers the copy living in
    //   the client's editor. The Entry class has no setVersion on purpose. The stored version is the only truth: a
    //   client that is behind has lost an edit and one that is ahead made its number up, so neither may write.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    private static void requireCurrentVersion(Entry entry, long clientVersion) {
        if (clientVersion != entry.getVersion()) {
            throw new EntryStaleException();
        }
    }
    // └── END ANSWER ▲ ──

    private LocalDate today(User user) {
        return LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
    }

    /** The memory date can't be in the future or before 1900-01-01 (D-09), so a typo can't create a week in year 202. */
    private static void checkMemoryDate(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            throw new ValidationFailedException("entryDate", "must not be after today");
        }
        if (date.isBefore(Entry.EARLIEST_ENTRY_DATE)) {
            throw new ValidationFailedException("entryDate", "must not be before " + Entry.EARLIEST_ENTRY_DATE);
        }
    }

    private static void checkContent(JsonNode contentJson) {
        if (contentJson != null) {
            EntryContentValidator.validate(contentJson);
        }
    }

    // TODO(human) PRACTICE: Write requireOwnedTags: turn the tagIds of a request into the user's own Tag objects.
    //   A tag that is someone else's and one that doesn't exist must look the same to the client, and an id that
    //   appears twice in the list is one tag. Think about the empty list and about what to hand back.
    // Hint: security.md (Ownership) and D-26. The repository finder only returns the user's tags, so compare how many
    //   distinct ids you asked for with how many came back. Why is 403 the wrong answer, and why must the error not
    //   say which id was wrong? The lock in findAndLockByIdInAndUserId is explained on the repository method.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    private List<Tag> requireOwnedTags(List<UUID> tagIds, UUID userId) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> wanted = new LinkedHashSet<>(tagIds);
        List<Tag> found = tags.findAndLockByIdInAndUserId(wanted, userId);
        if (found.size() != wanted.size()) {
            throw new NotFoundException("Tag");
        }
        return found;
    }
    // └── END ANSWER ▲ ──

    /** A template named in a request has to be the user's own; someone else's looks the same as one that isn't there. */
    private void checkTemplate(UUID templateId, UUID userId) {
        if (templateId != null
                && templates.findByIdAndUserId(templateId, userId).isEmpty()) {
            throw new NotFoundException("Template");
        }
    }

    /** Stores the caption and the plain text and preview line the server derives from it (D-05, D-24). */
    private static void setContent(Entry entry, JsonNode contentJson) {
        ContentTextExtractor.Extracted extracted = ContentTextExtractor.extract(contentJson);
        entry.setContentJson(contentJson);
        entry.setContentText(extracted.text());
        entry.setPreviewLine(extracted.previewLine());
    }

    /** Nothing to bring back (D-30): no title, no words in the caption, no photo or voice note. */
    private static boolean isEmpty(Entry entry) {
        return entry.getTitle() == null
                && entry.getContentText().isBlank()
                && entry.getMedia().isEmpty();
    }
}
