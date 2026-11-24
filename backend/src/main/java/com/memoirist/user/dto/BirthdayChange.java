package com.memoirist.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Objects;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * What a {@code PATCH /api/v1/users/me} body says about the birthday: set it to a date, or remove it. A body that doesn't
 * mention the birthday has no {@code BirthdayChange} at all ({@code null} in {@link UserUpdateRequest}), so the three
 * answers to "what do I do with the birthday?" are:
 *
 * <ul>
 *   <li>the field is missing: {@code null}, leave the birthday alone
 *   <li>{@code "birthday": null}: {@link #REMOVE}
 *   <li>{@code "birthday": "1990-05-17"}: {@link #to(LocalDate)}
 * </ul>
 *
 * <p>Why a type of its own: a plain {@code LocalDate} can't tell "missing" from "null" (Jackson gives {@code null} for
 * both), and in Jackson 3 an {@code Optional<LocalDate>} can't either (a missing property is {@code Optional.empty()},
 * the same as an explicit {@code null}; {@code UserUpdateRequestJsonTest} pins both facts). {@code JsonNullable} would
 * do it but is a new dependency. So the {@linkplain Reader reader} below returns {@link #REMOVE} for an explicit JSON
 * {@code null} and {@code null} for a missing property, and reads a date the way the rest of the API reads dates.
 *
 * @param date the new birthday, or {@code null} to remove it
 */
@JsonDeserialize(using = BirthdayChange.Reader.class)
@Schema(
        type = "string",
        format = "date",
        nullable = true,
        description = "The day the user was born (YYYY-MM-DD). Send null to remove it; leave the field out to keep it.")
public record BirthdayChange(LocalDate date) {

    /** Remove the birthday. */
    public static final BirthdayChange REMOVE = new BirthdayChange(null);

    /** Set the birthday to {@code date}. */
    public static BirthdayChange to(LocalDate date) {
        return new BirthdayChange(Objects.requireNonNull(date, "date"));
    }

    /** True when this change removes the birthday. */
    public boolean removes() {
        return date == null;
    }

    /**
     * Reads the {@code birthday} property. Jackson calls {@link #getNullValue} for a JSON {@code null} and does not
     * call {@link #deserialize} at all; for a property that is missing it calls {@link #getAbsentValue}, which I
     * override so that a missing birthday stays a Java {@code null}.
     */
    static final class Reader extends ValueDeserializer<BirthdayChange> {

        @Override
        public BirthdayChange deserialize(JsonParser parser, DeserializationContext context) {
            // Reads a string such as "1990-05-17" with the mapper's own LocalDate rules, so a malformed date fails
            // the same way (400 malformed-request) as it does for an entry date.
            return to(context.readValue(parser, LocalDate.class));
        }

        @Override
        public Object getNullValue(DeserializationContext context) {
            return REMOVE;
        }

        @Override
        public Object getAbsentValue(DeserializationContext context) {
            return null;
        }
    }
}
