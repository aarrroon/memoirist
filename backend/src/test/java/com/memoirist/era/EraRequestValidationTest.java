package com.memoirist.era;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.era.dto.EraCreateRequest;
import com.memoirist.era.dto.EraUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The rules written on the two Era request bodies, checked with a real Jakarta {@link Validator} and no Spring: which
 * field breaks which rule, what the constructors clean up, and that a rule about two dates names the field it is about.
 *
 * <p>Each rule is checked on {@link EraCreateRequest} and {@link EraUpdateRequest} together, because the contract says
 * a {@code PUT} takes the same body as a {@code POST} without the {@code id}.
 */
class EraRequestValidationTest {

    private static final String RANGE = "must be between 1900-01-01 and 9999-12-31";
    private static final String ORDER = "must not be before startDate";
    private static final LocalDate START = LocalDate.of(2026, 3, 1);

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    /** Field name to message, like the {@code fieldErrors} object in a response. */
    private static TreeMap<String, String> violations(Object request) {
        return validator.validate(request).stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (a, b) -> a + "; " + b,
                        TreeMap::new));
    }

    /** The violations of the same fields as a create body and as an update body; they must agree. */
    private static TreeMap<String, String> violations(
            String title, String description, LocalDate start, LocalDate end) {
        TreeMap<String, String> create = violations(new EraCreateRequest(null, title, description, start, end, false));
        TreeMap<String, String> update = violations(new EraUpdateRequest(title, description, start, end, false));
        assertThat(update)
                .as("a PUT body follows the same rules as a POST body")
                .isEqualTo(create);
        return create;
    }

    // Dates

    @Test
    void aStartDateAloneIsValidAndTheEraIsOngoing() {
        assertThat(violations("Uni", null, START, null)).isEmpty();
    }

    @Test
    void aOneDayEraHasTheSameStartAndEnd() {
        assertThat(violations("Gig night", null, START, START)).isEmpty();
    }

    @Test
    void anEndAfterTheStartIsValid() {
        assertThat(violations("Uni", null, START, START.plusYears(3))).isEmpty();
    }

    @Test
    void anEndBeforeTheStartIsReportedOnEndDate() {
        assertThat(violations("Uni", null, START, START.minusDays(1))).containsOnly(Map.entry("endDate", ORDER));
    }

    @Test
    void theStartDateIsRequired() {
        assertThat(violations("Uni", null, null, null)).containsOnly(Map.entry("startDate", "must not be null"));
        // With no start there is nothing to compare an end with, so the end is not blamed.
        assertThat(violations("Uni", null, null, START)).containsOnly(Map.entry("startDate", "must not be null"));
    }

    @Test
    void the1900AndYear9999EdgesAreInsideTheRangeAndOneDayBeyondThemIsNot() {
        assertThat(violations("Old", null, LocalDate.of(1900, 1, 1), LocalDate.of(9999, 12, 31)))
                .isEmpty();
        assertThat(violations("Too old", null, LocalDate.of(1899, 12, 31), null))
                .containsOnly(Map.entry("startDate", RANGE));
        assertThat(violations("Too far", null, START, LocalDate.of(10000, 1, 1)))
                .containsOnly(Map.entry("endDate", RANGE));
    }

    @Test
    void bothDatesCanBeWrongAtOnceAndEachIsNamed() {
        assertThat(violations("Both", null, LocalDate.of(1800, 1, 1), LocalDate.of(1700, 1, 1)))
                .containsOnly(Map.entry("startDate", RANGE), Map.entry("endDate", RANGE));
    }

    @Test
    void anEndBeforeAStartThatIsOutOfRangeIsOnlyBlamedOnTheStart() {
        // The start is the thing to fix. "end before start" would be a second, confusing message about the same date.
        assertThat(violations("Far", null, LocalDate.of(10000, 1, 1), LocalDate.of(2020, 1, 1)))
                .containsOnly(Map.entry("startDate", RANGE));
    }

    @Test
    void theMessagesNeverContainTheDatesTheClientSent() {
        TreeMap<String, String> found = violations("Old", null, LocalDate.of(1850, 6, 7), LocalDate.of(1840, 2, 3));

        assertThat(found.values()).noneMatch(message -> message.contains("1850") || message.contains("1840"));
    }

    // Title

    @Test
    void theTitleIsRequired() {
        assertThat(violations(null, null, START, null)).containsOnly(Map.entry("title", "must not be null"));
    }

    @ParameterizedTest(name = "\"{0}\" is empty once stripped")
    @ValueSource(strings = {"", " ", "   ", "\t\n  "})
    void aTitleOfOnlySpacesIsTooShort(String title) {
        assertThat(violations(title, null, START, null))
                .containsOnly(Map.entry("title", "size must be between 1 and 150"));
    }

    @Test
    void theTitleIsStrippedAndThenMeasured() {
        assertThat(new EraCreateRequest(null, "  Uni Days \t", null, START, null, false).title())
                .isEqualTo("Uni Days");
        assertThat(new EraUpdateRequest("  Uni Days ", null, START, null, false).title())
                .isEqualTo("Uni Days");
        assertThat(violations("  " + "t".repeat(150) + "  ", null, START, null)).isEmpty();
    }

    @Test
    void a150CharacterTitleIsFineAndA151CharacterOneIsNot() {
        assertThat(violations("t".repeat(150), null, START, null)).isEmpty();
        assertThat(violations("t".repeat(151), null, START, null))
                .containsOnly(Map.entry("title", "size must be between 1 and 150"));
    }

    // Description

    @Test
    void aDescriptionIsOptionalAndLimitedTo5000Characters() {
        assertThat(violations("Uni", null, START, null)).isEmpty();
        assertThat(violations("Uni", "d".repeat(5000), START, null)).isEmpty();
        assertThat(violations("Uni", "d".repeat(5001), START, null))
                .containsOnly(Map.entry("description", "size must be between 0 and 5000"));
    }

    @ParameterizedTest(name = "\"{0}\" becomes no description")
    @ValueSource(strings = {"", " ", "\t\n  "})
    void aDescriptionWithNothingLeftAfterStrippingBecomesNull(String description) {
        assertThat(new EraCreateRequest(null, "Uni", description, START, null, false).description())
                .isNull();
        assertThat(new EraUpdateRequest("Uni", description, START, null, false).description())
                .isNull();
    }

    @Test
    void aDescriptionIsStrippedButItsInnerWhitespaceIsKept() {
        assertThat(new EraCreateRequest(null, "Uni", "  First line.\n\nSecond line.  ", START, null, false)
                        .description())
                .isEqualTo("First line.\n\nSecond line.");
    }

    // Several wrong fields

    @Test
    void everyWrongFieldIsReportedTogether() {
        assertThat(violations(" ", "d".repeat(5001), LocalDate.of(1800, 1, 1), START))
                .containsOnlyKeys("title", "description", "startDate");
    }
}
