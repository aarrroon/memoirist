package com.memoirist.user;

import com.memoirist.common.MalformedRequestException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Phase 1's way of knowing who is calling: a header that names the user, with the seeded dev user when it is absent
 * (D-11). There is no login yet, so this stands in for one.
 *
 * <p>It exists only in the {@code local} and {@code test} profiles. In any other profile the bean isn't created, so
 * the header can't be used to pick a user, and an app that has no other {@link CurrentUserProvider} fails to start
 * instead of quietly letting everyone in. The dev user itself is seeded from {@code db/seed-local}, which only those
 * two profiles load.
 *
 * <p>The request is the injected {@link HttpServletRequest}. Spring gives a singleton like this one a proxy that looks
 * up the request of the calling thread every time it is used, so two requests at once each read their own header.
 */
@Component
@Profile({"local", "test"})
public class DevHeaderCurrentUserProvider implements CurrentUserProvider {

    /** The header that names the user. */
    public static final String HEADER = "X-Dev-User-Id";

    /** The user that {@code db/seed-local/R__seed_dev_user.sql} creates, and the one used when there is no header. */
    public static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final HttpServletRequest request;

    public DevHeaderCurrentUserProvider(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public UUID currentUserId() {
        // getHeaders, not getHeader: with two X-Dev-User-Id lines, getHeader quietly returns the first and ignores the
        // second. A request that names two users has no right answer, so it is refused.
        List<String> values = Collections.list(request.getHeaders(HEADER));
        if (values.isEmpty()) {
            return DEV_USER_ID;
        }
        if (values.size() > 1) {
            throw new MalformedRequestException(HEADER + " must be sent once.");
        }
        return parse(values.getFirst());
    }

    // TODO(human) PRACTICE: Turn the header's text into a UUID, or throw MalformedRequestException. Don't stop at
    //   UUID.fromString: try it on "1-1-1-1-1", "+0000000-0000-0000-0000-000000000001" and a last group that is one
    //   digit short. Decide what counts as a valid value (and whether an empty header does), and what the error message
    //   should leave out.
    // Hint: API_CONTRACT lists "bad X-Dev-User-Id" under malformed-request. An empty header is not "no header":
    //   a client that sends one has a bug, and the dev user's data would hide it. The exception's message goes to
    //   the client, so it must not repeat the value.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    private static final Pattern CANONICAL_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static UUID parse(String value) {
        if (!CANONICAL_UUID.matcher(value).matches()) {
            throw new MalformedRequestException(
                    HEADER + " must be a UUID such as " + DEV_USER_ID + " (8-4-4-4-12 hexadecimal digits).");
        }
        return UUID.fromString(value);
    }
    // └── END ANSWER ▲ ──
}
