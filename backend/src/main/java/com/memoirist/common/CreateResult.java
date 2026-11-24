package com.memoirist.common;

/**
 * What a client-supplied-id {@code POST} produced (D-12): the resource, and whether this call created it.
 *
 * <p>The controller needs the second part and the service is the only one who knows it: a new id answers
 * {@code 201 Created}, an id the user already has answers {@code 200 OK} with the stored resource (a safe retry, the
 * body is not applied again). Eras, templates and entries all create this way, so the answer has one shape.
 *
 * @param resource the response body: the new resource, or the stored one on a replay
 * @param created {@code true} for {@code 201}, {@code false} for the {@code 200} of a replay
 */
public record CreateResult<T>(T resource, boolean created) {

    public static <T> CreateResult<T> created(T resource) {
        return new CreateResult<>(resource, true);
    }

    public static <T> CreateResult<T> replayed(T resource) {
        return new CreateResult<>(resource, false);
    }
}
