package com.memoirist.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.memoirist.template.dto.TemplateCreateRequest;
import com.memoirist.template.dto.TemplateUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The rules written on the two Template request bodies, checked with a real Jakarta {@link Validator} and no Spring:
 * which field breaks which rule, what the constructors clean up, and that a bad list never crashes the constructor
 * (a crash there would be a {@code 500} where the contract promises a {@code 400}).
 *
 * <p>Each rule is checked on {@link TemplateCreateRequest} and {@link TemplateUpdateRequest} together, because the
 * contract says a {@code PUT} takes the same body as a {@code POST} without the {@code id}.
 */
class TemplateRequestValidationTest {

    private static final String SIZE_NAME = "size must be between 1 and 100";
    private static final String SIZE_SUBHEADING = "size must be between 1 and 200";
    private static final String SIZE_LIST = "size must be between 0 and 20";
    private static final String NOT_NULL = "must not be null";

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

    /**
     * Field name to message, like the {@code fieldErrors} object in a response. Spring names an item
     * {@code subheadings[2]}; the validator itself adds {@code .<list element>}, which is cut off here the way Spring
     * cuts it off.
     */
    private static TreeMap<String, String> violations(Object request) {
        return validator.validate(request).stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString().replace(".<list element>", ""),
                        ConstraintViolation::getMessage,
                        (a, b) -> a + "; " + b,
                        TreeMap::new));
    }

    /** The violations of the same fields as a create body and as an update body; they must agree. */
    private static TreeMap<String, String> violations(String name, List<String> subheadings) {
        TreeMap<String, String> create = violations(new TemplateCreateRequest(null, name, subheadings));
        TreeMap<String, String> update = violations(new TemplateUpdateRequest(name, subheadings));
        assertThat(update).as("PUT breaks the same rules as POST").isEqualTo(create);
        return create;
    }

    private static List<String> items(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "Prompt " + i).toList();
    }

    // The happy path

    @Test
    void aNameAndSomePromptsAreValid() {
        assertThat(violations("Trip day", List.of("Where did we go?", "Best moment")))
                .isEmpty();
    }

    @Test
    void anEmptyListOfSubheadingsIsValid() {
        assertThat(violations("Blank page", List.of())).isEmpty();
    }

    // The name

    @Test
    void theNameIsRequired() {
        assertThat(violations(null, List.of())).containsOnly(Map.entry("name", NOT_NULL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t\n"})
    void aNameOfOnlySpacesIsTooShort(String name) {
        assertThat(violations(name, List.of())).containsOnly(Map.entry("name", SIZE_NAME));
    }

    @Test
    void theNameIsStrippedAndThenMeasured() {
        assertThat(new TemplateCreateRequest(null, "  Trip day \n", List.of()).name())
                .isEqualTo("Trip day");
        assertThat(new TemplateUpdateRequest("  Trip day ", List.of()).name()).isEqualTo("Trip day");
        // 100 characters plus spaces on both sides is a 100-character name.
        assertThat(violations(" " + "n".repeat(100) + " ", List.of())).isEmpty();
    }

    @Test
    void a100CharacterNameIsFineAndA101CharacterOneIsNot() {
        assertThat(violations("n".repeat(100), List.of())).isEmpty();
        assertThat(violations("n".repeat(101), List.of())).containsOnly(Map.entry("name", SIZE_NAME));
    }

    // The list

    @Test
    void theListIsRequiredBecauseTheContractDoesNotMarkItOptional() {
        assertThat(violations("Trip day", null)).containsOnly(Map.entry("subheadings", NOT_NULL));
    }

    @Test
    void twentyItemsAreFineAndTwentyOneAreNot() {
        assertThat(violations("Trip day", items(20))).isEmpty();
        assertThat(violations("Trip day", items(21))).containsOnly(Map.entry("subheadings", SIZE_LIST));
    }

    @Test
    void anItemOf200CharactersIsFineAndOf201IsReportedByItsPosition() {
        assertThat(violations("Trip day", List.of("ok", "p".repeat(200)))).isEmpty();
        assertThat(violations("Trip day", List.of("ok", "p".repeat(201), "ok")))
                .containsOnly(Map.entry("subheadings[1]", SIZE_SUBHEADING));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "     ", "\t"})
    void anItemOfOnlySpacesIsEmptyAfterStrippingAndIsRejected(String blank) {
        assertThat(violations("Trip day", List.of("fine", blank)))
                .containsOnly(Map.entry("subheadings[1]", SIZE_SUBHEADING));
    }

    @Test
    void anItemIsStrippedBeforeItIsMeasured() {
        assertThat(new TemplateCreateRequest(null, "T", List.of("  Where?  ", "\tWho\n")).subheadings())
                .containsExactly("Where?", "Who");
        // 200 characters with spaces around them is a 200-character item.
        assertThat(violations("Trip day", List.of("  " + "p".repeat(200) + "  ")))
                .isEmpty();
    }

    @Test
    void aNullItemIsReportedByItsPositionAndDoesNotCrashTheConstructor() {
        // List.of() refuses a null, and so does List.copyOf(): a Jackson-built list can hold one, so this one is made
        // by hand. If the constructor threw here, the client would get a 500 for a body that is simply wrong.
        List<String> withNull = Arrays.asList("fine", null, "also fine");

        assertThat(violations("Trip day", withNull)).containsOnly(Map.entry("subheadings[1]", NOT_NULL));
    }

    @Test
    void theOrderIsKeptAndRepeatsAreAllowed() {
        List<String> repeated = List.of("Gratitude", "What happened?", "Gratitude");

        assertThat(violations("Check-in", repeated)).isEmpty();
        assertThat(new TemplateCreateRequest(null, "Check-in", repeated).subheadings())
                .containsExactly("Gratitude", "What happened?", "Gratitude");
    }

    @Test
    void theCleanedListCannotBeChangedAfterwards() {
        List<String> original = new ArrayList<>(List.of("a", "b"));
        TemplateCreateRequest request = new TemplateCreateRequest(null, "T", original);

        original.add("c");

        assertThat(request.subheadings()).containsExactly("a", "b");
        assertThrows(
                UnsupportedOperationException.class, () -> request.subheadings().add("x"));
    }

    // Together

    @Test
    void everyWrongFieldIsReportedTogether() {
        List<String> bad = new ArrayList<>(items(21));
        bad.set(3, " ");
        bad.set(4, "p".repeat(201));

        assertThat(violations("", bad))
                .containsOnly(
                        Map.entry("name", SIZE_NAME),
                        Map.entry("subheadings", SIZE_LIST),
                        Map.entry("subheadings[3]", SIZE_SUBHEADING),
                        Map.entry("subheadings[4]", SIZE_SUBHEADING));
    }

    @Test
    void theMessagesNeverContainWhatTheClientSent() {
        String secret = "my-private-prompt-" + "x".repeat(200);

        String all = violations(secret + secret, List.of(secret)).toString();

        assertThat(all).doesNotContain("my-private-prompt");
    }
}
