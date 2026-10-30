package com.memoirist.user;

import com.memoirist.common.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A person with a journal. In Phase 1 the only row is the dev user from the local seed (D-11); Phase 2 adds accounts.
 *
 * <p>{@code timezone} is an IANA zone name such as {@code Australia/Melbourne}. It decides what "today" and a week
 * are for this user (D-07). The entity trusts what it is given: {@code PATCH /users/me} checks the name with
 * {@link IanaTimeZone} before it gets here.
 */
@Entity
@Table(name = "users")
public class User extends TimestampedEntity {

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    /** Optional. Powers the next-birthday unlock suggestion (D-33). */
    @Column(name = "birthday")
    private LocalDate birthday;

    protected User() {}

    public User(UUID id, String email, String displayName, String timezone) {
        super(id);
        this.email = email;
        this.displayName = displayName;
        this.timezone = timezone;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }
}
