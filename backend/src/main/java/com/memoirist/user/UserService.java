package com.memoirist.user;

import com.memoirist.common.NotFoundException;
import com.memoirist.user.dto.UserResponse;
import com.memoirist.user.dto.UserUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current user's own record ({@code /users/me}), and the one place that turns "who is calling" into a
 * {@link User} that exists.
 *
 * <p>{@link CurrentUserProvider} only reads the id from the request. It doesn't check that anyone has that id (D-11),
 * and a create that inserts a row for nobody dies on the foreign key with a {@code 500}. So <strong>every service
 * that creates a row starts with {@link #currentUser()}</strong>: a user who isn't there is {@code 404 not-found}, as
 * for any other resource that isn't found. Most creates need the user anyway, for the time zone that decides "today"
 * and the week (D-07, D-08).
 */
@Service
@Transactional
public class UserService {

    private final UserRepository users;
    private final CurrentUserProvider currentUser;

    public UserService(UserRepository users, CurrentUserProvider currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    /**
     * The user this request is for. Inside a transaction (a create's) the entity is managed by that transaction.
     *
     * @throws NotFoundException if the id in the request names no one
     * @throws com.memoirist.common.MalformedRequestException if the request names its user in a way that can't be read
     */
    @Transactional(readOnly = true)
    public User currentUser() {
        return users.findById(currentUser.currentUserId()).orElseThrow(() -> new NotFoundException("User"));
    }

    @Transactional(readOnly = true)
    public UserResponse get() {
        return UserMapper.toResponse(currentUser());
    }

    /**
     * Applies the fields that are present and leaves the rest. There is no {@code save}: the user is a managed entity
     * inside this transaction, so Hibernate sees which fields changed and writes one {@code UPDATE} at commit (and none
     * when nothing changed). The request has already been validated, so the values are safe to store.
     */
    public UserResponse update(UserUpdateRequest request) {
        User user = currentUser();
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.timezone() != null) {
            user.setTimezone(request.timezone());
        }
        return UserMapper.toResponse(user);
    }
}
