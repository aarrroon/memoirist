package com.memoirist.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memoirist.user.dto.BirthdayChange;
import com.memoirist.user.dto.UserUpdateRequest;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * How the PATCH body is read from JSON, with Jackson alone and no Spring: the one thing a plain
 * {@code LocalDate birthday} could not express. API_CONTRACT (Users) gives the birthday three meanings, and the service
 * trusts the three shapes this class pins down.
 */
class UserUpdateRequestJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private UserUpdateRequest read(String body) {
        return json.readValue(body, UserUpdateRequest.class);
    }

    // TODO(human) PRACTICE: Prove that the three ways a client can write the birthday arrive as three different Java
    //   values: the field missing, the field set to null, and the field set to a date. Then show that the other two
    //   fields are not affected (a body with only a name has a missing birthday).
    // Hint: D-33 and API_CONTRACT (Users). A plain LocalDate gives null for both "missing" and "null", and in Jackson 3
    //   an Optional<LocalDate> gives Optional.empty() for both (the second test below proves it), which is why the
    //   record holds a BirthdayChange. Assert the missing case with isNull() and the other two with isEqualTo(...).
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    @Test
    void aMissingBirthdayIsJavaNull() {
        assertThat(read("{}").birthday()).isNull();
        assertThat(read("{\"displayName\":\"Sam\"}").birthday()).isNull();
    }

    @Test
    void aNullBirthdayIsTheRemoveChange() {
        BirthdayChange change = read("{\"birthday\":null}").birthday();

        assertThat(change).isEqualTo(BirthdayChange.REMOVE);
        assertThat(change.removes()).isTrue();
    }

    @Test
    void aDateIsASetChange() {
        BirthdayChange change = read("{\"birthday\":\"1990-05-17\"}").birthday();

        assertThat(change).isEqualTo(BirthdayChange.to(LocalDate.of(1990, 5, 17)));
        assertThat(change.removes()).isFalse();
    }
    // └── END ANSWER ▲ ──

    @Test
    void theOtherFieldsStillMeanNothingWhenNullOrMissing() {
        UserUpdateRequest request = read("{\"displayName\":null,\"birthday\":\"1990-05-17\"}");

        assertThat(request.displayName()).isNull();
        assertThat(request.timezone()).isNull();
    }

    /** The reason {@link BirthdayChange} exists: an Optional cannot tell "missing" from "null" in Jackson 3. */
    private record WithOptional(Optional<LocalDate> birthday) {}

    @Test
    void anOptionalCannotTellMissingFromNullSoItIsNotUsed() {
        Optional<LocalDate> missing = json.readValue("{}", WithOptional.class).birthday();
        Optional<LocalDate> explicitNull =
                json.readValue("{\"birthday\":null}", WithOptional.class).birthday();

        assertThat(missing).isEmpty();
        assertThat(explicitNull).isEmpty();
    }

    @Test
    void aMalformedDateIsAJsonErrorNotAChange() {
        assertThatThrownBy(() -> read("{\"birthday\":\"not-a-date\"}")).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> read("{\"birthday\":\"1990-02-30\"}")).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> read("{\"birthday\":true}")).isInstanceOf(JacksonException.class);
    }
}
