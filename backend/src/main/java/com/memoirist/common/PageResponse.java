package com.memoirist.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of a list, in the shape every paged endpoint returns (API_CONTRACT, Pagination). Spring's own {@link Page}
 * is never serialised: its JSON carries Spring's internals (sort, pageable, first, last) and can change between
 * versions, so a client would depend on the framework instead of on this contract.
 *
 * @param items the rows of this page, already mapped to their response type
 * @param page the zero-based page number that was asked for
 * @param size the page size that was asked for (the last page may hold fewer rows)
 * @param totalItems how many rows the whole list has
 * @param totalPages how many pages of {@code size} the whole list makes; 0 for an empty list
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    /**
     * The page that {@code source} describes, holding {@code items} instead of {@code source}'s rows. The rows are
     * passed separately because the service maps them as a group (the feed loads every week's entries in one query), so
     * a row-by-row mapping function would not fit.
     */
    public static <T> PageResponse<T> of(Page<?> source, List<T> items) {
        return new PageResponse<>(
                items, source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
