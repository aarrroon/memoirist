package com.memoirist.era;

import com.memoirist.common.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A user-named chapter of life, defined by its dates alone (D-22). Nothing links an Era to its weeks or entries in
 * the database: a week belongs to every Era whose dates overlap it, and the service works that out.
 */
@Entity
@Table(name = "eras")
public class Era extends TimestampedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** {@code null} means the Era is ongoing. */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** A Spotify URI; the format is checked in the service (D-35). */
    @Column(name = "soundtrack", length = 100)
    private String soundtrack;

    /** One of the user's own photos (D-36), kept as a plain id: the service checks that it is theirs. */
    @Column(name = "cover_media_id")
    private UUID coverMediaId;

    @Column(name = "pinned_in_feed", nullable = false)
    private boolean pinnedInFeed;

    protected Era() {}

    public Era(UUID id, UUID userId, String title, LocalDate startDate) {
        super(id);
        this.userId = userId;
        this.title = title;
        this.startDate = startDate;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getSoundtrack() {
        return soundtrack;
    }

    public void setSoundtrack(String soundtrack) {
        this.soundtrack = soundtrack;
    }

    public UUID getCoverMediaId() {
        return coverMediaId;
    }

    public void setCoverMediaId(UUID coverMediaId) {
        this.coverMediaId = coverMediaId;
    }

    public boolean isPinnedInFeed() {
        return pinnedInFeed;
    }

    public void setPinnedInFeed(boolean pinnedInFeed) {
        this.pinnedInFeed = pinnedInFeed;
    }
}
