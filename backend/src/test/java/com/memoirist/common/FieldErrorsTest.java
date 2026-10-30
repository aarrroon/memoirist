package com.memoirist.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link FieldErrors} on its own: plain objects, no Spring. These pin the shape that API_CONTRACT (Errors) promises:
 * one string per field, and the same output whatever order Spring reports the failures in.
 */
class FieldErrorsTest {

    @Test
    void oneMessagePerFieldBecomesTheFlatMapTheContractShows() {
        FieldErrors errors = new FieldErrors();
        errors.add("entryDate", "must not be after today");

        assertThat(errors.asMap()).isEqualTo(Map.of("entryDate", "must not be after today"));
        assertThat(errors.summary()).isEqualTo("entryDate must not be after today");
    }

    @Test
    void severalFailuresOnOneFieldAreJoinedInAlphabeticalOrder() {
        FieldErrors errors = new FieldErrors();
        errors.add("name", "size must be between 3 and 150");
        errors.add("name", "must not be blank");

        assertThat(errors.asMap())
                .containsExactly(Map.entry("name", "must not be blank; size must be between 3 and 150"));
        assertThat(errors.summary()).isEqualTo("name must not be blank; name size must be between 3 and 150");
    }

    @Test
    void theOrderSpringReportsInDoesNotChangeTheOutput() {
        FieldErrors forwards = new FieldErrors();
        FieldErrors backwards = new FieldErrors();
        List<String> fields = List.of("title", "note", "tags[1].name");
        fields.forEach(field -> forwards.add(field, "is invalid"));
        fields.reversed().forEach(field -> backwards.add(field, "is invalid"));

        assertThat(forwards.asMap().keySet()).containsExactly("note", "tags[1].name", "title");
        assertThat(backwards.asMap()).isEqualTo(forwards.asMap());
        assertThat(backwards.summary()).isEqualTo(forwards.summary());
    }

    @Test
    void theSameMessageTwiceIsShownOnce() {
        FieldErrors errors = new FieldErrors();
        errors.add("title", "must not be blank");
        errors.add("title", "must not be blank");

        assertThat(errors.asMap()).containsEntry("title", "must not be blank");
    }

    @Test
    void aMissingMessageBecomesAGenericOneInsteadOfNull() {
        FieldErrors errors = new FieldErrors();
        errors.add("title", null);
        errors.addGeneral(null);

        assertThat(errors.asMap()).containsEntry("title", "is invalid");
        assertThat(errors.summary()).isEqualTo("title is invalid; is invalid");
    }

    @Test
    void aMessageNotTiedToAFieldIsInTheDetailButNotInFieldErrors() {
        FieldErrors errors = new FieldErrors();
        errors.addGeneral("the unlock date must be after the entry date");

        assertThat(errors.hasNoFields()).isTrue();
        assertThat(errors.asMap()).isEmpty();
        assertThat(errors.summary()).isEqualTo("the unlock date must be after the entry date");
    }

    @Test
    void nothingCollectedGivesAnEmptySummary() {
        assertThat(new FieldErrors().summary()).isEmpty();
    }
}
