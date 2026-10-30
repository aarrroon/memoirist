package com.memoirist.tag;

import com.memoirist.common.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** One of the user's own categories for entries (D-26). Names are unique per user, ignoring case. */
@Entity
@Table(name = "tags")
public class Tag extends TimestampedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    protected Tag() {}

    public Tag(UUID id, UUID userId, String name) {
        super(id);
        this.userId = userId;
        this.name = name;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
