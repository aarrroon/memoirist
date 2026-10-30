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

    protected Media() {}

    public Media(
            UUID id,
            Entry entry,
            String objectKey,
            String mediaType,
            String purpose,
            int displayOrder,
            long fileSizeBytes) {
        super(id);
        this.entry = entry;
        this.objectKey = objectKey;
        this.mediaType = mediaType;
        this.purpose = purpose;
        this.displayOrder = displayOrder;
        this.fileSizeBytes = fileSizeBytes;
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
}
