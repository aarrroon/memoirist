package com.memoirist.week.dto;

import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT /api/v1/weekly-logs/{logId}}: a full replace of what the user may set on a week (API_CONTRACT,
 * Weekly logs). A week's Eras follow from their dates and can't be set here (D-22), and its soundtrack is added in
 * task 3.14, so until then a {@code soundtrack} in the body is ignored like any other field the endpoint doesn't know.
 *
 * <p>Being a full replace, a {@code title} that is left out, {@code null} or only spaces clears the title.
 *
 * @param title up to 200 characters once stripped of spaces at both ends; {@code null} for no title
 */
public record WeeklyLogUpdateRequest(@Size(max = 200) String title) {

    public WeeklyLogUpdateRequest {
        title = cleaned(title);
    }

    /** Stripped; {@code null} when there is nothing left, so "no title" has one spelling. */
    private static String cleaned(String title) {
        if (title == null) {
            return null;
        }
        String stripped = title.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
