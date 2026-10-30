package com.memoirist.era;

import java.time.LocalDate;

/**
 * The two dates that define an Era (D-22). Both request records ({@code EraCreateRequest}, {@code EraUpdateRequest})
 * implement it, so the single class-level rule {@link ValidEraDates} can check either of them.
 */
public interface EraDates {

    LocalDate startDate();

    /** {@code null} means the Era is ongoing. */
    LocalDate endDate();
}
