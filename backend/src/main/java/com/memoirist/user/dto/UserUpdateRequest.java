package com.memoirist.user.dto;

import com.memoirist.user.IanaTimeZone;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PATCH /api/v1/users/me} (API_CONTRACT, Users). Send only what changes: a field that is missing,
 * or {@code null}, is left alone. There is no field for the email, the id or the dates, so a body that names one
 * changes nothing (Jackson skips properties the record doesn't have).
 *
 * <p>The name is stripped before it is checked, so {@code "  Aaron "} is saved as {@code "Aaron"} and {@code "   "}
 * counts as empty. That is why the rule is {@code @Size(min = 1)} and not {@code @NotBlank}: {@code @NotBlank} also
 * rejects {@code null}, and here {@code null} means "no change". The birthday joins in task 3.13.
 *
 * @param displayName 1 to 100 characters after stripping
 * @param timezone an IANA zone such as {@code Australia/Melbourne} (D-07)
 */
public record UserUpdateRequest(
        @Size(min = 1, max = 100) String displayName,
        @IanaTimeZone String timezone) {

    public UserUpdateRequest {
        displayName = displayName == null ? null : displayName.strip();
    }
}
