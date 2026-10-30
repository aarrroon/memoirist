package com.memoirist.common;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * An exception as the server log may show it: the class of the exception and of each cause, and the stack frames,
 * but <em>never a message</em>.
 *
 * <p>Why not log the exception itself: a message can hold what the user wrote. PostgreSQL ends a constraint failure
 * with {@code Detail: Failing row contains (...)}, and that row holds the entry's title and text (a sealed Loot Drop's
 * too), a unique-key failure on a tag names the tag, and a storage error can hold a host and a bucket.
 * {@code .claude/rules/security.md} says never to log entry content, and D-10 says nothing of a sealed entry leaks, so
 * the handler logs {@code ScrubbedException.of(exception)} where it would have logged {@code exception}.
 *
 * <p>What is kept is enough to find the bug: which exceptions, in which order, and where in the code. For a
 * {@link SQLException} the five-character SQLState goes along ({@code 23514} is a failed CHECK, {@code 23505} a
 * duplicate key), because that is a standard code and holds no data. Suppressed exceptions are dropped, since they
 * carry messages too. This class never calls {@code getMessage()} or {@code toString()} on what it copies.
 *
 * <p>Logback prints it as {@code com.memoirist.common.ScrubbedException: org.postgresql.util.PSQLException (SQLState
 * 23514)}, then the frames, then {@code Caused by:} for each cause.
 *
 * <p>This covers what our code logs. A library may log the same exception itself (Hibernate does for SQL errors), and
 * {@code ErrorHandlingIntegrationTest} checks that the app's log as a whole holds no entry text.
 */
final class ScrubbedException extends RuntimeException {

    /** Far more than any real chain, and a stop for a chain that loops back on itself. */
    private static final int MAX_CHAIN = 20;

    private ScrubbedException(String description, ScrubbedException cause) {
        // Suppression off (nothing to attach), stack trace writable (the copy takes the original's frames).
        super(description, cause, false, true);
    }

    static ScrubbedException of(Throwable original) {
        return copy(original, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static ScrubbedException copy(Throwable original, Set<Throwable> seen) {
        seen.add(original);
        Throwable cause = original.getCause();
        ScrubbedException scrubbedCause =
                cause == null || seen.contains(cause) || seen.size() >= MAX_CHAIN ? null : copy(cause, seen);
        ScrubbedException copy = new ScrubbedException(describe(original), scrubbedCause);
        copy.setStackTrace(original.getStackTrace());
        return copy;
    }

    private static String describe(Throwable original) {
        String name = original.getClass().getName();
        if (original instanceof SQLException sql && sql.getSQLState() != null) {
            return name + " (SQLState " + sql.getSQLState() + ")";
        }
        return name;
    }
}
