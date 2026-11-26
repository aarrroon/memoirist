package com.memoirist.media;

import com.memoirist.common.BaseEntity;
import com.memoirist.entry.Entry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One item in an entry's carousel: a photo, a diary-page photo or a voice note. Only the object key is stored, never
 * a URL (non-negotiable 5); the API presigns it when it builds the response.
 *
 * <p>It extends {@link BaseEntity} rather than {@code TimestampedEntity}: the {@code media} table has
 * {@code uploaded_at} and no {@code created_at} or {@code updated_at} (D-12).
 */
@Entity
@Table(name = "media")
public class Media extends BaseEntity {

    /** An ordinary photo or other image. */
    public static final String PURPOSE_PHOTO = "PHOTO";

    /** A photo of a paper diary page; it must be an image (D-20). */
    public static final String PURPOSE_PAGE_SCAN = "PAGE_SCAN";

    /**
     * The owning entry. Media is owned through it, so a user-scoped lookup of media goes through
     * {@code entry.userId} ({@code MediaRepository.findByIdAndEntryUserId}).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false, updatable = false)
    private Entry entry;

    /** {@code users/{userId}/entries/{entryId}/{mediaId}.{ext}} */
    @Column(name = "object_key", nullable = false, updatable = false, length = 512)
    private String objectKey;

    /** One of the D-17 MIME types; checked in the service. */
    @Column(name = "media_type", nullable = false, length = 100)
    private String mediaType;

    /** {@link #PURPOSE_PHOTO} or {@link #PURPOSE_PAGE_SCAN}, checked in the service so a new one needs no migration. */
    @Column(name = "purpose", nullable = false, length = 20)
    private String purpose;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    /**
     * The picture's size in pixels as read from its header at upload (D-17, V2), or {@code null} for a voice note and
     * for an image whose header couldn't be read. Both or neither: {@code chk_media_dimensions}.
     */
    @Column(name = "width", updatable = false)
    private Integer width;

    @Column(name = "height", updatable = false)
    private Integer height;

    /**
     * The thumbnail's object key (480 px on the longest side, D-43, V3), or {@code null} when the photo has none: a
     * voice note, a photo no bigger than a thumbnail, or one uploaded before task 4.18 or by a client that makes none.
     */
    @Column(name = "thumbnail_key", updatable = false, length = 512)
    private String thumbnailKey;

    /** The screen copy's object key (1600 px on the longest side, D-43, V3), or {@code null} as for the thumbnail. */
    @Column(name = "screen_key", updatable = false, length = 512)
    private String screenKey;

    protected Media() {}

    /** A media item with no stored size: a voice note, or an image whose header couldn't be read. */
    public Media(
            UUID id,
            Entry entry,
            String objectKey,
            String mediaType,
            String purpose,
            int displayOrder,
            long fileSizeBytes) {
        this(id, entry, objectKey, mediaType, purpose, displayOrder, fileSizeBytes, null);
    }

    /** @param size the picture's size, or {@code null} when it has none */
    public Media(
            UUID id,
            Entry entry,
            String objectKey,
            String mediaType,
            String purpose,
            int displayOrder,
            long fileSizeBytes,
            ImageSize size) {
        super(id);
        this.entry = entry;
        this.objectKey = objectKey;
        this.mediaType = mediaType;
        this.purpose = purpose;
        this.displayOrder = displayOrder;
        this.fileSizeBytes = fileSizeBytes;
        this.width = size == null ? null : size.width();
        this.height = size == null ? null : size.height();
    }

    /**
     * A photo with its smaller copies (D-43). Either key may be {@code null}; only an image may have one
     * ({@code chk_media_copies_images_only}).
     */
    public Media(
            UUID id,
            Entry entry,
            String objectKey,
            String mediaType,
            String purpose,
            int displayOrder,
            long fileSizeBytes,
            ImageSize size,
            String thumbnailKey,
            String screenKey) {
        this(id, entry, objectKey, mediaType, purpose, displayOrder, fileSizeBytes, size);
        this.thumbnailKey = thumbnailKey;
        this.screenKey = screenKey;
    }

    /** The owning entry. A lazy proxy: reading only its id costs no query, anything else loads the entry. */
    public Entry getEntry() {
        return entry;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getPurpose() {
        return purpose;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    /** In pixels, or {@code null} (see {@link #width}). */
    public Integer getWidth() {
        return width;
    }

    /** In pixels, or {@code null} exactly when {@link #getWidth()} is. */
    public Integer getHeight() {
        return height;
    }

    /** The thumbnail's key, or {@code null} (see {@link #thumbnailKey}). */
    public String getThumbnailKey() {
        return thumbnailKey;
    }

    /** The screen copy's key, or {@code null}. */
    public String getScreenKey() {
        return screenKey;
    }

    /**
     * Every file this row points at: the original, then each copy it has. Removing the photo deletes all of them
     * (D-30, D-43), so a caller that deletes files asks for this list, never for {@link #getObjectKey()} alone.
     */
    public List<String> storedKeys() {
        List<String> keys = new ArrayList<>(3);
        keys.add(objectKey);
        if (thumbnailKey != null) {
            keys.add(thumbnailKey);
        }
        if (screenKey != null) {
            keys.add(screenKey);
        }
        return List.copyOf(keys);
    }
}
