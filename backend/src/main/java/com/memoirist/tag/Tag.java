package com.memoirist.tag;

import com.memoirist.common.NameCollation;
import com.memoirist.common.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Comparator;
import java.util.UUID;

/** One of the user's own categories for entries (D-26). Names are unique per user, ignoring case. */
@Entity
@Table(name = "tags")
public class Tag extends TimestampedEntity {

    /** The longest name, in characters (API_CONTRACT, Tags). The column is {@code VARCHAR(50)}. */
    public static final int MAX_NAME_LENGTH = 50;

    /**
     * The order tags are listed in, on {@code GET /tags} and in an entry's {@code tags}: by name, ignoring case and
     * accents ({@link NameCollation}). Two tags can only look the same when a spelling differs by accent, since the
     * database refuses names that differ only in case, but the order still falls back to the exact spelling, then
     * oldest first, then the id, so a list never depends on the order the database returned rows in.
     */
    public static final Comparator<Tag> BY_NAME = Comparator.comparing(Tag::getName, NameCollation.BASE_LETTERS_ORDER)
            .thenComparing(Tag::getName)
            .thenComparing(Tag::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Tag::getId);

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
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
