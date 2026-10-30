package com.memoirist.template;

import com.memoirist.common.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A named list of headings that a new entry can start from. */
@Entity
@Table(name = "templates")
public class Template extends TimestampedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** A JSON array of strings (up to 20, each up to 200 characters, checked in the service). */
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
