package com.memoirist.era.dto;

import com.memoirist.era.EraDates;
import com.memoirist.era.ValidEraDates;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The body of {@code POST /api/v1/eras} (API_CONTRACT, Eras). {@code id} is the client's own UUID, so a retry of the
 * same request finds the Era it already made (D-12); leave it out and the server picks one.
 *
 * <p>There is no field for {@code soundtrack} or {@code coverMediaId} yet (tasks 3.14 and 3.15): a body that names
 * them is read as if it did not, because Jackson skips properties the record doesn't have.
 *
 * <p>The text is cleaned in the constructor, before it is validated: the title and description lose the spaces at
 * both ends, and a description with nothing left becomes {@code null}. {@code pinnedInFeed} defaults to
 * {@code false}, so it is never {@code null} in the record. It is a {@code Boolean} so that a missing or {@code null}
 * value is read as {@code false} whatever Jackson does with a {@code null} for a primitive.
 *
 * @param title 1 to 150 characters once stripped
 * @param description up to 5000 characters, or {@code null} for none
 * @param endDate {@code null} means the Era is ongoing
 */
@ValidEraDates
public record EraCreateRequest(
        UUID id,
        @NotNull @Size(min = 1, max = 150) String title,
        @Size(max = 5000) String description,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        Boolean pinnedInFeed)
        implements EraDates {

    public EraCreateRequest {
        title = title == null ? null : title.strip();
        description = EraText.description(description);
        pinnedInFeed = Boolean.TRUE.equals(pinnedInFeed);
    }
}
