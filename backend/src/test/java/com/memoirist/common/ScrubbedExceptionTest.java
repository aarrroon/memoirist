package com.memoirist.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/**
 * {@link ScrubbedException} is what the server log gets in place of an exception, so these tests are about what must
 * not be in it (a message, anywhere in the chain) and what must (the classes, the frames, the SQLState).
 */
class ScrubbedExceptionTest {

    @Test
    void noMessageFromAnywhereInTheChainSurvives() {
        SQLException database = new SQLException(
                "ERROR: new row violates check constraint\n  Detail: Failing row contains (SECRET-ENTRY-TEXT)",
                "23514");
        IllegalStateException service = new IllegalStateException("could not save entry SECRET-TITLE", database);
        service.addSuppressed(new RuntimeException("suppressed SECRET-SUPPRESSED"));

        String printed = print(ScrubbedException.of(service));

        assertThat(printed)
                .doesNotContain("SECRET")
                .doesNotContain("Failing row")
                .doesNotContain("check constraint")
                .doesNotContain("suppressed");
    }

    @Test
    void theClassOfEachExceptionInTheChainAndTheSqlStateSurvive() {
        SQLException database = new SQLException("message", "23514");
        IllegalStateException service = new IllegalStateException("message", database);

        String printed = print(ScrubbedException.of(service));

        assertThat(printed)
                .contains("ScrubbedException: java.lang.IllegalStateException")
                .contains("Caused by: com.memoirist.common.ScrubbedException: java.sql.SQLException (SQLState 23514)");
    }

    @Test
    void anSqlExceptionWithoutAStateIsJustItsClass() {
        assertThat(ScrubbedException.of(new SQLException("message")).getMessage())
                .isEqualTo("java.sql.SQLException");
    }

    @Test
    void theStackFramesAreTheOriginalsSoTheLogStillPointsAtTheCode() {
        RuntimeException original = new RuntimeException("message");

        ScrubbedException scrubbed = ScrubbedException.of(original);

        assertThat(scrubbed.getStackTrace()).isNotEmpty().isEqualTo(original.getStackTrace());
        assertThat(print(scrubbed)).contains("at com.memoirist.common.ScrubbedExceptionTest.");
    }

    @Test
    void aChainThatLoopsBackOnItselfEndsInsteadOfRunningForever() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);

        ScrubbedException scrubbed = ScrubbedException.of(first);

        // first -> second, and the link back to first is dropped.
        assertThat(scrubbed.getCause()).isNotNull();
        assertThat(scrubbed.getCause().getCause()).isNull();
    }

    private static String print(Throwable throwable) {
        StringWriter out = new StringWriter();
        throwable.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
