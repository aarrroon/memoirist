package com.memoirist.template;

import com.memoirist.common.NameCollation;
import com.memoirist.common.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A named list of headings that a new entry can start from. */
@Entity
@Table(name = "templates")
public class Template extends TimestampedEntity {

    /** The longest name, in characters (API_CONTRACT, Templates). The column is {@code VARCHAR(100)}. */
    public static final int MAX_NAME_LENGTH = 100;

    /** The most subheadings one template can have. */
    public static final int MAX_SUBHEADINGS = 20;

    /** The longest subheading, in characters. The array is JSONB, so only the request rules hold this limit. */
    public static final int MAX_SUBHEADING_LENGTH = 200;

    /**
     * The order templates are listed in: by name, ignoring case and accents ({@link NameCollation}). Names that look
     * the same come in the order of their exact spelling, then oldest first, then by id, so a list never depends on the
     * order the database happened to return rows in. The sort happens in Java because the database's order depends on
     * the collation Postgres was created with, which differs between a laptop, a CI container and a hosted database.
     */
    public static final Comparator<Template> BY_NAME = Comparator.comparing(
                    Template::getName, NameCollation.BASE_LETTERS_ORDER)
            .thenComparing(Template::getName)
            .thenComparing(Template::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Template::getId);

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    /** A JSON array of strings: up to {@link #MAX_SUBHEADINGS}, each up to {@link #MAX_SUBHEADING_LENGTH} characters. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "subheadings", nullable = false)
    private List<String> subheadings = new ArrayList<>();

    protected Template() {}

    public Template(UUID id, UUID userId, String name, List<String> subheadings) {
        super(id);
        this.userId = userId;
        this.name = name;
        this.subheadings = new ArrayList<>(subheadings);
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

    public List<String> getSubheadings() {
        return List.copyOf(subheadings);
    }

    /** Replaces the whole list. Hibernate notices a new list; it can't see a change made inside the old one. */
    public void setSubheadings(List<String> subheadings) {
        this.subheadings = new ArrayList<>(subheadings);
    }
}
