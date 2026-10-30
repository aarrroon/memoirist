package com.memoirist.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.user.dto.UserUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The rules written on the PATCH body, checked with a real Jakarta {@link Validator} and no Spring: which field breaks
 * which rule, and that the name is stripped before it is measured.
 */
class UserUpdateRequestTest {

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
    private static TreeMap<String, String> violations(UserUpdateRequest request) {
        return validator.validate(request).stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (a, b) -> a + "; " + b,
                        TreeMap::new));
    }

    @Test
    void anEmptyRequestIsValid() {
        assertThat(violations(new UserUpdateRequest(null, null))).isEmpty();
    }

    @Test
    void theNameIsStrippedBeforeItIsChecked() {
        assertThat(new UserUpdateRequest("  Sam \t", null).displayName()).isEqualTo("Sam");
    }

    @ParameterizedTest(name = "\"{0}\" is empty once stripped")
    @ValueSource(strings = {"", " ", "   ", "\t\n  "})
    void aNameOfOnlySpacesIsTooShort(String name) {
        assertThat(violations(new UserUpdateRequest(name, null)))
                .containsOnlyKeys("displayName")
                .containsEntry("displayName", "size must be between 1 and 100");
    }

    @Test
    void aHundredCharactersAreFineAndAHundredAndOneAreNot() {
        assertThat(violations(new UserUpdateRequest("a".repeat(100), null))).isEmpty();
        assertThat(violations(new UserUpdateRequest("a".repeat(101), null))).containsOnlyKeys("displayName");
    }

    @Test
    void spacesAroundAHundredCharactersDoNotCount() {
        assertThat(violations(new UserUpdateRequest("  " + "a".repeat(100) + "  ", null)))
                .isEmpty();
    }

    @Test
    void aWrongZoneAndAWrongNameAreBothReported() {
        assertThat(violations(new UserUpdateRequest("", "Mars/Olympus_Mons")))
                .containsOnlyKeys("displayName", "timezone")
                .containsEntry("timezone", "must be an IANA time zone, such as Australia/Melbourne");
    }
}
