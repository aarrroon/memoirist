package com.memoirist.user;

import com.memoirist.common.NotFoundException;
import com.memoirist.common.ValidationFailedException;
import com.memoirist.user.dto.BirthdayChange;
import com.memoirist.user.dto.UserResponse;
import com.memoirist.user.dto.UserUpdateRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final Clock clock;

    public UserService(UserRepository users, CurrentUserProvider currentUser, Clock clock) {
        this.users = users;
        this.currentUser = currentUser;
        this.clock = clock;
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
     * when nothing changed). The request has already been validated, so the values are safe to store, except the
     * birthday, which needs the clock and the user's zone and is checked here <em>before anything is changed</em>.
     *
     * <p>The birthday has three states (see {@link UserUpdateRequest}): missing leaves it alone, {@code null} removes
     * it and a date sets it.
     *
     * @throws ValidationFailedException if the birthday is after today (in the zone the user will have once this
     *     request is applied) or before 1900-01-01
     */
    public UserResponse update(UserUpdateRequest request) {
        User user = currentUser();
        BirthdayChange birthday = request.birthday();
        if (birthday != null && !birthday.removes()) {
            String zone = request.timezone() != null ? request.timezone() : user.getTimezone();
            checkBirthday(birthday.date(), LocalDate.now(clock.withZone(ZoneId.of(zone))));
        }
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.timezone() != null) {
            user.setTimezone(request.timezone());
        }
        if (birthday != null) {
            user.setBirthday(birthday.date());
        }
        return UserMapper.toResponse(user);
    }

    // TODO(human) PRACTICE: Write checkBirthday: refuse a birthday that cannot be real, using the user's today.
    //   "Today" is passed in already worked out in the user's own zone. Decide which side of today is allowed (is
    //   today itself fine?), whether there is a floor too, and what the error should say without repeating the date
    //   the client sent.
    // Hint: API_CONTRACT (Users): a birthday after today is 400. The entries use the same 1900-01-01 floor (D-09), and
    //   a floor also keeps a silly year out of the database. ValidationFailedException(field, message) becomes a 400
    //   with fieldErrors, the same shape as the entry-date check in EntryService.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    private static void checkBirthday(LocalDate birthday, LocalDate today) {
        if (birthday.isAfter(today)) {
            throw new ValidationFailedException("birthday", "must not be after today");
        }
        if (birthday.isBefore(User.EARLIEST_BIRTHDAY)) {
            throw new ValidationFailedException("birthday", "must not be before " + User.EARLIEST_BIRTHDAY);
        }
    }
    // └── END ANSWER ▲ ──
}
