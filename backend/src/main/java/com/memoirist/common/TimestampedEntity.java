package com.memoirist.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A {@link BaseEntity} with {@code created_at} and {@code updated_at} (D-12). Hibernate fills both in, so no
 * service sets them. They are {@code Instant}s, stored as {@code TIMESTAMPTZ} in UTC (D-07).
 *
 * <p>Every entity extends this except {@code Media}: its table has {@code uploaded_at} instead of the two columns,
 * and {@code ddl-auto=validate} would refuse the mapping.
 *
 * <p>These two annotations take the time from the JVM's clock, not from the injected {@code Clock} bean. That is
 * deliberate: they stamp audit columns that no rule reads. Anything that decides something by time, such as a seal or
 * a week boundary, uses the {@code Clock}.
 */
@MappedSuperclass
public abstract class TimestampedEntity extends BaseEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TimestampedEntity() {}

    protected TimestampedEntity(UUID id) {
        super(id);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
