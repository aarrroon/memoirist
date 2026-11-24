package com.memoirist.week;

import com.memoirist.common.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

/**
 * One Monday-to-Sunday week of a user's journal, in their timezone (D-08). Created automatically when the first
 * entry lands in it. It has no link to Eras (D-22): the service matches the user's Eras to it by date.
 *
 * <p>{@code @DynamicUpdate} makes Hibernate's {@code UPDATE} name only the columns that changed. Without it every update
 * writes all of them, so a title saved on one device while the week is closed on another could put the old
 * {@code is_closed} back: the row has no {@code @Version}, and the two requests change different columns, which is no
 * reason for one to undo the other.
 */
@Entity
@Table(name = "weekly_logs")
@DynamicUpdate
public class WeeklyLog extends TimestampedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", length = 200)
    private String title;

    /** Always a Monday (a CHECK constraint says so). */
    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    /** Always {@code startDate + 6} (a CHECK constraint says so). */
    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Column(name = "is_closed", nullable = false)
    private boolean closed;

    /** A Spotify URI (D-35). */
    @Column(name = "soundtrack", length = 100)
    private String soundtrack;

    protected WeeklyLog() {}

    public WeeklyLog(UUID id, UUID userId, LocalDate startDate) {
        super(id);
        this.userId = userId;
        this.startDate = startDate;
        this.endDate = startDate.plusDays(6);
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public String getSoundtrack() {
        return soundtrack;
    }

    public void setSoundtrack(String soundtrack) {
        this.soundtrack = soundtrack;
    }
}
