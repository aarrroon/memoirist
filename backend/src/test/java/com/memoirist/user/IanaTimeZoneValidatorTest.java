package com.memoirist.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Which texts count as a time zone. Plain unit test: the validator keeps no state and needs no Spring, so the whole
 * table runs in milliseconds.
 *
 * <p>Several of the rejected texts are ones {@code ZoneId.of} <em>accepts</em>. That is the point of the table: the
 * zone is stored, sent to the browser, and used for "today" (D-07), so it has to be a name the browser's
 * {@code Intl.DateTimeFormat} also understands.
 */
class IanaTimeZoneValidatorTest {

    private final IanaTimeZoneValidator validator = new IanaTimeZoneValidator();

    @ParameterizedTest(name = "{0} is a zone")
    @ValueSource(
            strings = {
                "Australia/Melbourne",
                "Australia/Lord_Howe",
                "America/New_York",
                "America/Argentina/Buenos_Aires",
                "Asia/Kolkata",
                "Asia/Calcutta",
                "Europe/London",
                "Pacific/Auckland",
                "UTC",
                "Etc/GMT+10"
            })
    void acceptsIanaNames(String zone) {
        assertThat(validator.isValid(zone, null)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" is not a zone")
    @ValueSource(
            strings = {
                "",
                "Mars/Olympus_Mons",
                "Melbourne",
                "Australia",
                "australia/melbourne",
                "AUSTRALIA/MELBOURNE",
                " Australia/Melbourne",
                "Australia/Melbourne ",
                "Australia/Melbourne\n",
                "+10:00",
                "UTC+10",
                "GMT+10:00",
                "Z",
                "EST",
                "SystemV/AST4",
                "../etc/passwd",
                "Australia/Melbourne'; DROP TABLE users;--"
            })
    void rejectsEverythingElse(String text) {
        assertThat(validator.isValid(text, null)).isFalse();
    }

    @Test
    void nullIsValidBecauseItMeansNoChange() {
        // Jakarta's rule for every constraint: null passes, and @NotNull says whether a field may be null. In a PATCH
        // a missing timezone is null, and must not be an error.
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" is a zone to Java but not to us")
    @ValueSource(strings = {"+10:00", "UTC+10", "GMT+10:00", "Z"})
    void javaAcceptsTheseButTheBrowserWouldNot(String text) {
        // The reason the rule is not simply "ZoneId.of doesn't throw".
        ZoneId.of(text);
        assertThat(validator.isValid(text, null)).isFalse();
    }

    @Test
    void everyZoneJavaKnowsExceptTheObsoleteSystemVOnesIsAcceptedAndFitsTheColumn() {
        // users.timezone is VARCHAR(64). The longest accepted name must fit, or a valid zone would fail in the
        // database with a 500 instead of being saved.
        ZoneId.getAvailableZoneIds().stream()
                .filter(id -> !id.startsWith("SystemV/"))
                .forEach(id -> {
                    assertThat(validator.isValid(id, null)).as(id).isTrue();
                    assertThat(id.length()).as(id).isLessThanOrEqualTo(64);
                });
    }
}
