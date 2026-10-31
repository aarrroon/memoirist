package com.memoirist.media;

import com.memoirist.common.EntrySealedException;
import com.memoirist.common.NotFoundException;
import com.memoirist.common.PayloadTooLargeException;
import com.memoirist.common.ValidationFailedException;
import com.memoirist.entry.Entry;
import com.memoirist.entry.EntryRepository;
import com.memoirist.entry.EntrySeal;
import com.memoirist.media.dto.MediaOrderRequest;
import com.memoirist.media.dto.MediaResponse;
import com.memoirist.storage.StorageService;
import com.memoirist.user.CurrentUserProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * The photos, diary-page photos and voice notes of an entry (API_CONTRACT, Media): upload, reorder and delete.
 *
 * <ul>
 *   <li><b>Ownership (non-negotiable 3).</b> The entry or media item is looked up by id <em>and</em> the current user.
 *       Someone else's, one that isn't there and one in Recently deleted are the same {@code 404} (D-30).
 *   <li><b>The seal (D-10).</b> A sealed Loot Drop's media can't be added to, reordered or deleted
 *       ({@code 409 entry-sealed}), and none of its files is signed. A Loot Drop that has opened can be changed.
 *   <li><b>The order (D-17).</b> The positions of an entry's media are always {@code 0, 1, 2, ...} with no gap. A new
 *       item goes in at the end, or at the position asked for; deleting one closes the gap. Every call that changes the
 *       positions first locks the entry's row, so two calls never edit the same entry's carousel at once.
 *   <li><b>Files and rows (TECH_STACK, Storage).</b> A file is stored first and its row written second, and the file
 *       is taken out again if the row doesn't get written. A file is deleted only after the transaction that removed
 *       its row has committed.
 * </ul>
 *
 * <p>Media changes do not touch the entry's {@code version}: a photo added while the user types a caption must not make
 * the caption's {@code PUT} stale (D-23).
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final EntryRepository entries;
    private final MediaRepository media;
    private final MediaMapper mapper;
    private final StorageService storage;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    /** A short, read-only transaction: the look before an upload. */
    private final TransactionTemplate reading;

    /** A short transaction that writes: the row after an upload. */
    private final TransactionTemplate writing;

    public MediaService(
            EntryRepository entries,
            MediaRepository media,
            MediaMapper mapper,
            StorageService storage,
            CurrentUserProvider currentUser,
            Clock clock,
            PlatformTransactionManager transactions) {
        this.entries = entries;
        this.media = media;
        this.mapper = mapper;
        this.storage = storage;
        this.currentUser = currentUser;
        this.clock = clock;
        this.reading = new TransactionTemplate(transactions);
        this.reading.setReadOnly(true);
        this.writing = new TransactionTemplate(transactions);
    }

    /**
     * Adds one photo, diary-page photo or voice note to the entry.
     *
     * <p><b>This method is deliberately not {@code @Transactional}.</b> Storing a 25 MB file takes seconds, and a
     * transaction would hold a database connection (and the entry's row lock) the whole time, so a handful of
     * uploads at once could use up the connection pool. It works in three steps instead:
     * <ol>
     *   <li>A short read-only transaction checks everything that can be checked early, so a request that is going to
     *       fail doesn't upload anything.
     *   <li>The file is stored, with no transaction open.
     *   <li>A short write transaction locks the entry, checks again (the entry may have been sealed, deleted or filled
     *       in the meantime), makes room at the position and inserts the row.
     * </ol>
     * If anything goes wrong once storing has begun, the file is deleted again.
     *
     * @param requestedOrder where to put it ({@code 0} is first), or {@code null} for the end
     * @param requestedPurpose {@code PHOTO} (the default when {@code null}) or {@code PAGE_SCAN}
     * @throws NotFoundException the entry isn't the user's, doesn't exist or is in Recently deleted
     * @throws EntrySealedException the entry is a sealed Loot Drop
     * @throws ValidationFailedException the file is missing or empty, the purpose is unknown, or the position is out
     *     of range, or the entry already holds 20 items
     * @throws com.memoirist.common.UnsupportedFileTypeException the type isn't allowed, or a diary page isn't an image
     * @throws PayloadTooLargeException the file is over 25 MB
     * @throws com.memoirist.common.StorageUnavailableException storage failed
     */
    public MediaResponse upload(UUID entryId, MultipartFile file, Integer requestedOrder, String requestedPurpose) {
        UUID userId = currentUser.currentUserId();

        Upload upload = reading.execute(status -> {
            Entry entry = findEntry(entryId, userId);
            refuseIfSealed(entry);
            Upload checked = check(file, requestedPurpose, requestedOrder);
            checkRoom(media.countByEntryId(entryId), requestedOrder);
            return checked;
        });

        UUID mediaId = UUID.randomUUID();
        String objectKey = objectKey(userId, entryId, mediaId, upload.type());

        // TODO(human) PRACTICE: A file in storage and a row in the database can't be committed together, so write the
        //   compensation: store the file, then insert the row in its own short transaction, and if either step fails
        //   take the file back out again before the exception goes on. Decide what happens when taking it out fails
        //   too, and which exception the caller should see.
        // Hint: TECH_STACK (Storage, Upload flow): "If the insert fails, delete the object (best effort)". The second
        //   failure must not hide the first, because the first is what the user needs to hear about. storage.delete
        //   throws StorageUnavailableException; log the key (an id, never a URL) and the exception class, not its
        //   message. The write transaction is a TransactionTemplate, so an exception thrown inside it rolls it back.
        // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
        try {
            try (InputStream content = file.getInputStream()) {
                storage.put(objectKey, content, file.getSize(), upload.type());
            } catch (IOException unreadableUpload) {
                throw new UncheckedIOException("The uploaded file couldn't be read back from disk", unreadableUpload);
            }
            return writing.execute(
                    status -> insert(entryId, userId, mediaId, objectKey, upload, file.getSize(), requestedOrder));
        } catch (RuntimeException failure) {
            deleteQuietly(objectKey);
            throw failure;
        }
        // └── END ANSWER ▲ ──
    }

    /**
     * Puts the entry's media in the order given: every one of them, once. Positions that don't change are not written.
     *
     * @throws ValidationFailedException an id is repeated, isn't one of the entry's media, or one is left out
     */
    @Transactional
    public List<MediaResponse> reorder(UUID entryId, MediaOrderRequest request) {
        UUID userId = currentUser.currentUserId();
        Entry entry = lockEntry(entryId, userId);
        refuseIfSealed(entry);

        List<Media> current = media.findByEntryIdAndEntryUserIdOrderByDisplayOrderAsc(entryId, userId);
        List<Media> reordered = inRequestedOrder(current, request.mediaIds());
        renumber(reordered);
        return reordered.stream().map(item -> mapper.toResponse(entryId, item)).toList();
    }

    /**
     * Deletes one photo, diary page or voice note: its row now, its file once this has committed. The items after it
     * move up one place, so the positions stay {@code 0, 1, 2, ...}.
     */
    @Transactional
    public void delete(UUID mediaId) {
        UUID userId = currentUser.currentUserId();
        UUID entryId = media.findByIdAndEntryUserId(mediaId, userId)
                .orElseThrow(() -> new NotFoundException("Media"))
                .getEntry()
                .getId();
        Entry entry = lockEntry(entryId, userId);
        refuseIfSealed(entry);

        // Read the list only now that the lock is held: another call may have changed it while this one waited.
        List<Media> items = new ArrayList<>(media.findByEntryIdAndEntryUserIdOrderByDisplayOrderAsc(entryId, userId));
        Media doomed = items.stream()
                .filter(item -> item.getId().equals(mediaId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Media"));
        items.remove(doomed);
        media.delete(doomed);
        renumber(items);
        storage.deleteAfterCommit(List.of(doomed.getObjectKey()));
    }

    // ---- Upload ---------------------------------------------------------------------------------------------------

    /** What the request checks found out about the file: its allowed type and its purpose. */
    private record Upload(String type, String purpose) {}

    /**
     * The checks that need no database, in the order the contract promises: the file is there and has bytes, the
     * purpose is known, the position isn't negative (all {@code 400}), then the type (and a diary page being an image,
     * {@code 415}), then the size ({@code 413}).
     */
    private static Upload check(MultipartFile file, String requestedPurpose, Integer requestedOrder) {
        if (file == null) {
            throw new ValidationFailedException("file", "must not be null");
        }
        if (file.isEmpty()) {
            throw new ValidationFailedException("file", "must not be empty");
        }
        String purpose = UploadRules.purpose(requestedPurpose);
        if (requestedOrder != null && requestedOrder < 0) {
            throw new ValidationFailedException("displayOrder", "must not be negative");
        }
        String type = UploadRules.allowedType(file.getContentType());
        UploadRules.requireImageForPageScan(purpose, type);
        if (file.getSize() > UploadRules.MAX_FILE_BYTES) {
            // Spring's multipart limit normally stops a file this big before it reaches here; this is the same rule
            // written where the code can be tested without sending 25 MB, and where a changed setting can't undo it.
            throw new PayloadTooLargeException("A file can be at most 25 MB.");
        }
        return new Upload(type, purpose);
    }

    /** The entry has a free place, and the position asked for (if any) is somewhere in the carousel or just after it. */
    private static void checkRoom(long itemCount, Integer requestedOrder) {
        if (itemCount >= UploadRules.MAX_ITEMS_PER_ENTRY) {
            throw new ValidationFailedException(
                    "file", "an entry can hold at most " + UploadRules.MAX_ITEMS_PER_ENTRY + " photos and voice notes");
        }
        if (requestedOrder != null && requestedOrder > itemCount) {
            throw new ValidationFailedException("displayOrder", "must be between 0 and " + itemCount);
        }
    }

    /** The write transaction of an upload. Runs with the entry's row locked, and checks again what step one checked. */
    private MediaResponse insert(
            UUID entryId,
            UUID userId,
            UUID mediaId,
            String objectKey,
            Upload upload,
            long fileSize,
            Integer requestedOrder) {
        Entry entry = lockEntry(entryId, userId);
        refuseIfSealed(entry);
        List<Media> items = media.findByEntryIdAndEntryUserIdOrderByDisplayOrderAsc(entryId, userId);
        checkRoom(items.size(), requestedOrder);

        int position = requestedOrder == null ? items.size() : requestedOrder;
        // Make room: everything from the position on moves down one place. For a moment two rows share a position,
        // which the deferred unique constraint allows until the commit (V1, uq_media_entry_order).
        items.subList(position, items.size()).forEach(later -> later.setDisplayOrder(later.getDisplayOrder() + 1));
        Media created = media.saveAndFlush(
                new Media(mediaId, entry, objectKey, upload.type(), upload.purpose(), position, fileSize));
        return mapper.toResponse(entryId, created);
    }

    /** {@code users/{userId}/entries/{entryId}/{mediaId}.{ext}}: the key shape TECH_STACK (Storage) and the schema give. */
    private static String objectKey(UUID userId, UUID entryId, UUID mediaId, String type) {
        return "users/" + userId + "/entries/" + entryId + "/" + mediaId + "." + UploadRules.extensionFor(type);
    }

    /** Best effort: a file that can't be removed is wasted space, and the failure that got us here matters more. */
    private void deleteQuietly(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException cleanupFailure) {
            log.warn(
                    "Could not delete stored object {} after a failed upload ({}); it stays in storage",
                    objectKey,
                    cleanupFailure.getClass().getName());
        }
    }

    // ---- Reorder --------------------------------------------------------------------------------------------------

    /**
     * The entry's media in the requested order, after checking that the request names each of them exactly once. The
     * three ways to get it wrong have three messages, none of which repeats an id.
     */
    private static List<Media> inRequestedOrder(List<Media> current, List<UUID> requested) {
        Set<UUID> requestedIds = new HashSet<>();
        for (UUID id : requested) {
            if (!requestedIds.add(id)) {
                throw new ValidationFailedException("mediaIds", "must not list the same id twice");
            }
        }
        Set<UUID> currentIds = new HashSet<>();
        current.forEach(item -> currentIds.add(item.getId()));
        if (!currentIds.containsAll(requestedIds)) {
            throw new ValidationFailedException("mediaIds", "must only list media that belong to this entry");
        }
        if (requestedIds.size() < currentIds.size()) {
            throw new ValidationFailedException("mediaIds", "must list every photo and voice note of the entry");
        }
        Map<UUID, Media> byId = current.stream().collect(Collectors.toMap(Media::getId, Function.identity()));
        return requested.stream().map(byId::get).toList();
    }

    /** Gives each item the position of its place in the list. An item that is already there is not written. */
    private static void renumber(List<Media> inOrder) {
        for (int position = 0; position < inOrder.size(); position++) {
            inOrder.get(position).setDisplayOrder(position);
        }
    }

    // ---- Lookups and the seal -------------------------------------------------------------------------------------

    private Entry findEntry(UUID entryId, UUID userId) {
        return entries.findByIdAndUserId(entryId, userId).orElseThrow(() -> new NotFoundException("Entry"));
    }

    /** The user's entry, with its row locked until the transaction ends. */
    private Entry lockEntry(UUID entryId, UUID userId) {
        return entries.findForUpdateByIdAndUserId(entryId, userId).orElseThrow(() -> new NotFoundException("Entry"));
    }

    private void refuseIfSealed(Entry entry) {
        if (EntrySeal.isSealed(entry, Instant.now(clock))) {
            throw new EntrySealedException(
                    "This entry is sealed. Its photos and voice notes can't change until it opens.");
        }
    }
}
