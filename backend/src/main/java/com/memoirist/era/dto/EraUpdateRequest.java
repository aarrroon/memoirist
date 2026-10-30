package com.memoirist.era.dto;

import com.memoirist.era.EraDates;
import com.memoirist.era.ValidEraDates;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * The body of {@code PUT /api/v1/eras/{eraId}}: the same as {@link EraCreateRequest} without the {@code id}, because
 * the path names the Era. A {@code PUT} replaces the whole Era, so a field that is left out is reset: no description,
 * ongoing, not pinned.
 *
 * @param title 1 to 150 characters once stripped
 * @param description up to 5000 characters, or {@code null} for none
 * @param endDate {@code null} means the Era is ongoing
 */
@ValidEraDates
public record EraUpdateRequest(
        @NotNull @Size(min = 1, max = 150) String title,
        @Size(max = 5000) String description,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        Boolean pinnedInFeed)
        implements EraDates {

    public EraUpdateRequest {
        title = title == null ? null : title.strip();
        description = EraText.description(description);
        pinnedInFeed = Boolean.TRUE.equals(pinnedInFeed);
    }
}
