package com.memoirist.entry;

import com.memoirist.common.TimestampedEntity;
import com.memoirist.media.Media;
import com.memoirist.tag.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * One journal entry: an Instagram-style post with a memory date, a caption, media and tags.
 *
 * <p><b>Never turn an {@code Entry} into a response by hand.</b> {@code EntryMapper} is the only place that does, because
 * it hides everything about a sealed Loot Drop (D-10, non-negotiable 4). This class has no idea whether it is sealed.
 *
 * <p><b>Recently deleted (D-30).</b> {@code @SQLRestriction} adds {@code deleted_at IS NULL} to every query and load of
 * this entity, so a deleted entry is invisible by default and a new finder can't forget to leave it out. The only
 * reads of deleted entries are the two native queries in {@code EntryRepository}, which Hibernate does not rewrite.
 *
 * <p><b>Foreign keys are plain ids</b> ({@code userId}, {@code weeklyLogId}, {@code templateId},
 * {@code replyToEntryId}), not associations, because nothing walks them: services compare ids and look the other
 * side up through its own user-scoped repository. The two real associations are the ones read together with the
 * entry, {@link #getMedia()} and {@link #getTags()}.
 */
@Entity
@Table(name = "entries")
@SQLRestriction("deleted_at IS NULL")
public class Entry extends TimestampedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** The week that contains {@code entryDate}, kept in step by the service. */
    @Column(name = "weekly_log_id", nullable = false)
    private UUID weeklyLogId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "title", length = 150)
    private String title;

    /** The memory date: when it happened, not when it was written. */
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    /** A TipTap document (D-05), validated by the service before it gets here. {@code null} means no caption. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json")
    private JsonNode contentJson;

    /** Plain text derived from {@link #contentJson} by the server (D-05). */
    @Column(name = "content_text", nullable = false)
    private String contentText = "";

    /** Derived by the server whenever the content is saved (D-24). */
    @Column(name = "preview_line", length = 120)
    private String previewLine;

    /** A Spotify URI (D-35). */
    @Column(name = "soundtrack", length = 100)
    private String soundtrack;

    /** The user's own opened Loot Drop that this entry answers (D-33). */
    @Column(name = "reply_to_entry_id")
    private UUID replyToEntryId;

    /** Sealed while this is after now (D-10). */
    @Column(name = "unlock_date")
    private Instant unlockDate;

    /** The background unlock job's own flag. */
    @Column(name = "unlock_notified", nullable = false)
    private boolean unlockNotified;

    /** When the user saw the opened Loot Drop in the Opened strip; cleared whenever the entry is sealed (D-10). */
    @Column(name = "unlock_seen_at")
    private Instant unlockSeenAt;

    /** Set while the entry waits in Recently deleted (D-30). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** The memory date is a guess, such as a photo-dump photo with no date of its own (D-34). */
    @Column(name = "date_unknown", nullable = false)
    private boolean dateUnknown;

    /**
     * Hibernate's optimistic lock (D-23): 0 when the entry is first saved, and one more after every change that is
     * written. Hibernate adds {@code AND version = ?} to each UPDATE, so a save based on an older read changes no row
     * and fails. There is no setter: the client's version is compared with this one in the service and never copied
     * onto it.
     *
     * <p>A primitive is enough. Whether an entry is new is {@link #isNew()}'s job (D-12), and Hibernate doesn't read a
     * version of 0 as "unsaved" (see {@code PersistableInsertTest}), so it needn't be {@code null} before the first INSERT.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * The entry's photos and voice notes, in carousel order. Read-only from here: media rows are written through
     * {@code MediaRepository} (an upload adds one at a time) and removed by the database when the entry goes
     * ({@code ON DELETE CASCADE}), after the service has deleted the stored files. One query loads the media of up to
     * 50 entries at once.
     */
    @OneToMany(mappedBy = "entry", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @BatchSize(size = 50)
    private List<Media> media = new ArrayList<>();

    /**
     * The entry's tags, through the {@code entry_tags} table. The table's {@code source} and {@code created_at} columns
     * are filled by their defaults ({@code 'USER'} and {@code NOW()}); Phase 4 needs an {@code EntryTag} entity to
     * record {@code 'AI'}.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "entry_tags",
            joinColumns = @JoinColumn(name = "entry_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @BatchSize(size = 50)
    private Set<Tag> tags = new HashSet<>();

    protected Entry() {}

    public Entry(UUID id, UUID userId, UUID weeklyLogId, LocalDate entryDate) {
        super(id);
        this.userId = userId;
        this.weeklyLogId = weeklyLogId;
        this.entryDate = entryDate;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getWeeklyLogId() {
        return weeklyLogId;
    }

    public void setWeeklyLogId(UUID weeklyLogId) {
        this.weeklyLogId = weeklyLogId;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public JsonNode getContentJson() {
        return contentJson;
    }

    public void setContentJson(JsonNode contentJson) {
        this.contentJson = contentJson;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public String getPreviewLine() {
        return previewLine;
    }

    public void setPreviewLine(String previewLine) {
        this.previewLine = previewLine;
    }

    public String getSoundtrack() {
        return soundtrack;
    }

    public void setSoundtrack(String soundtrack) {
        this.soundtrack = soundtrack;
    }

    public UUID getReplyToEntryId() {
        return replyToEntryId;
    }

    public void setReplyToEntryId(UUID replyToEntryId) {
        this.replyToEntryId = replyToEntryId;
    }

    public Instant getUnlockDate() {
        return unlockDate;
    }

    public void setUnlockDate(Instant unlockDate) {
        this.unlockDate = unlockDate;
    }

    public boolean isUnlockNotified() {
        return unlockNotified;
    }

    public void setUnlockNotified(boolean unlockNotified) {
        this.unlockNotified = unlockNotified;
    }

    public Instant getUnlockSeenAt() {
        return unlockSeenAt;
    }

    public void setUnlockSeenAt(Instant unlockSeenAt) {
        this.unlockSeenAt = unlockSeenAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDateUnknown() {
        return dateUnknown;
    }

    public void setDateUnknown(boolean dateUnknown) {
        this.dateUnknown = dateUnknown;
    }

    public long getVersion() {
        return version;
    }

    /** The entry's media in carousel order. Loads them on first use, so call it inside the service's transaction. */
    public List<Media> getMedia() {
        return Collections.unmodifiableList(media);
    }

    /** The entry's tags. Loads them on first use, so call it inside the service's transaction. */
    public Set<Tag> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    /** Makes these the entry's tags. Keeps the same collection, so Hibernate writes only the rows that changed. */
    public void replaceTags(Collection<Tag> newTags) {
        tags.retainAll(newTags);
        tags.addAll(newTags);
    }
}
