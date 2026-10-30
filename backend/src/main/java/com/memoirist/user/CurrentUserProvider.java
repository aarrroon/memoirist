package com.memoirist.user;

import java.util.UUID;

/**
 * Who is making this request? (D-11)
 *
 * <p>This is the only way code learns the current user. A controller never reads a user id from a body, a query
 * string or a path, and a service never takes one as a parameter that a client could have chosen. Every lookup of user
 * data then starts from {@code currentUserId()} (non-negotiable 3).
 *
 * <p>Phase 1 has one implementation, {@link DevHeaderCurrentUserProvider}, which exists only in the {@code local} and
 * {@code test} profiles. Phase 2 adds one that reads a JWT; callers don't change, because they only see this interface.
 *
 * <p>Implementations answer for the request being handled on the calling thread, so call this from a request thread.
 * A background job has no current user: it works through users itself.
 */
public interface CurrentUserProvider {

    /**
     * The id of the user this request is for.
     *
     * @throws com.memoirist.common.MalformedRequestException if the request names its user in a way that can't be read
     */
    UUID currentUserId();
}
