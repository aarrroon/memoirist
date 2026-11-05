package com.memoirist.user.dto;

import com.memoirist.user.IanaTimeZone;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PATCH /api/v1/users/me} (API_CONTRACT, Users). Send only what changes: a {@code displayName} or
 * {@code timezone} that is missing, or {@code null}, is left alone. There is no field for the email, the id or the
 * dates, so a body that names one changes nothing (Jackson skips properties the record doesn't have).
 *
 * <p>The name is stripped before it is checked, so {@code "  Aaron "} is saved as {@code "Aaron"} and {@code "   "}
 * counts as empty. That is why the rule is {@code @Size(min = 1)} and not {@code @NotBlank}: {@code @NotBlank} also
 * rejects {@code null}, and here {@code null} means "no change".
 *
 * <p><b>The birthday has three states, because {@code null} has to mean something there</b> (D-33: the user can remove
 * it again): a field that is missing leaves it alone, {@code "birthday": null} removes it, and a date sets it. A plain
 * {@code LocalDate} can't tell the first two apart, so the component is a {@link BirthdayChange} (see there for why
 * not {@code Optional} or {@code JsonNullable}). Whether the date is sensible (not after today, not before 1900) needs
 * the clock and the user's zone, so {@code UserService} checks it.
 *
 * @param displayName 1 to 100 characters after stripping
 * @param timezone an IANA zone such as {@code Australia/Melbourne} (D-07)
 * @param birthday {@code null} when the field is missing; {@link BirthdayChange#REMOVE} for an explicit {@code null};
 *     otherwise the new birthday
 */
public record UserUpdateRequest(
        @Size(min = 1, max = 100) String displayName,
        @IanaTimeZone String timezone,
        BirthdayChange birthday) {

    public UserUpdateRequest {
        displayName = displayName == null ? null : displayName.strip();
    }
}
