package com.memoirist.week;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.week.dto.WeeklyLogUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The rules on the body of {@code PUT /weekly-logs/{logId}}, checked with a real Jakarta {@link Validator} and no
 * Spring: what the record cleans up in its constructor, and the one length rule. The endpoint tests prove the same
 * through HTTP; these pin the edges cheaply (API_CONTRACT, Weekly logs: {@code title} is 0 to 200 characters or null).
 */
class WeeklyLogRequestValidationTest {

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

    private static Set<ConstraintViolation<WeeklyLogUpdateRequest>> violations(String title) {
        return validator.validate(new WeeklyLogUpdateRequest(title));
    }

    @Test
    void aTitleIsStrippedOfSpacesAtBothEnds() {
        assertThat(new WeeklyLogUpdateRequest("  Grampians Trip Week \t\n").title())
                .isEqualTo("Grampians Trip Week");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t\n"})
    void noTitleAnEmptyTitleAndATitleOfOnlySpacesAllMeanNoTitle(String title) {
        assertThat(new WeeklyLogUpdateRequest(title).title()).isNull();
        assertThat(violations(title)).isEmpty();
    }

    @Test
    void aTitleOf200CharactersPassesAndOneOverFailsOnTheTitle() {
        assertThat(violations("t".repeat(200))).isEmpty();

        Set<ConstraintViolation<WeeklyLogUpdateRequest>> tooLong = violations("t".repeat(201));

        assertThat(tooLong).hasSize(1);
        ConstraintViolation<WeeklyLogUpdateRequest> violation =
                tooLong.iterator().next();
        assertThat(violation.getPropertyPath()).hasToString("title");
        assertThat(violation.getMessage()).isEqualTo("size must be between 0 and 200");
    }

    @Test
    void theLengthIsCountedAfterTheSpacesAreStripped() {
        assertThat(violations("  " + "t".repeat(200) + "  ")).isEmpty();
        assertThat(violations("  " + "t".repeat(201) + "  ")).hasSize(1);
    }
}
