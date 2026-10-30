package com.memoirist.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memoirist.Fixtures;
import com.memoirist.common.MalformedRequestException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Reading the user from the header, without Spring or a database: the provider is given a {@link MockHttpServletRequest}
 * directly. The same behaviour over HTTP is in {@link CurrentUserHttpTest}.
 */
class DevHeaderCurrentUserProviderTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final DevHeaderCurrentUserProvider provider = new DevHeaderCurrentUserProvider(request);

    @Test
    void usesTheUserNamedByTheHeader() {
        UUID someone = UUID.randomUUID();
        request.addHeader("X-Dev-User-Id", someone.toString());

        assertThat(provider.currentUserId()).isEqualTo(someone);
    }

    @Test
    void fallsBackToTheSeededDevUserWithoutTheHeader() {
        // Compared with the id typed out in Fixtures, not with the constant under test, so a typo in the constant
        // fails.
        assertThat(provider.currentUserId()).isEqualTo(Fixtures.DEV_USER_ID);
    }

    @Test
    void acceptsCapitalLettersAndReturnsTheSameUser() {
        UUID someone = UUID.randomUUID();
        request.addHeader("X-Dev-User-Id", someone.toString().toUpperCase());

        assertThat(provider.currentUserId()).isEqualTo(someone);
    }

    @Test
    void headerNameIsNotCaseSensitive() {
        UUID someone = UUID.randomUUID();
        request.addHeader("x-dev-user-id", someone.toString());

        assertThat(provider.currentUserId()).isEqualTo(someone);
    }

    @Test
    void readsTheRequestOfTheMomentNotTheOneItWasBuiltWith() {
        // The provider is created once and lives for the whole app. Each call must look at the current request.
        UUID first = UUID.randomUUID();
        request.addHeader("X-Dev-User-Id", first.toString());
        assertThat(provider.currentUserId()).isEqualTo(first);

        // A different user next, not just "no header": an answer kept from the first call would survive the second.
        UUID second = UUID.randomUUID();
        request.removeHeader("X-Dev-User-Id");
        request.addHeader("X-Dev-User-Id", second.toString());
        assertThat(provider.currentUserId()).isEqualTo(second);

        request.removeHeader("X-Dev-User-Id");
        assertThat(provider.currentUserId()).isEqualTo(Fixtures.DEV_USER_ID);
    }

    /**
     * Values that UUID.fromString would accept or that look close to a UUID. None of them is the canonical form.
     * The first three are the surprising ones: {@code UUID.fromString} turns each into a real, different UUID.
     */
    @ParameterizedTest(name = "[{index}] {0} is malformed")
    @ValueSource(
            strings = {
                "1-1-1-1-1",
                "00000000-0000-0000-0000-00000000001",
                "+0000000-0000-0000-0000-000000000001",
                "00000000-0000-0000-0000-0000000000011",
                "00000000000000000000000000000001",
                "{00000000-0000-0000-0000-000000000001}",
                "urn:uuid:00000000-0000-0000-0000-000000000001",
                " 00000000-0000-0000-0000-000000000001",
                "00000000-0000-0000-0000-000000000001 ",
                "00000000-0000-0000-0000-00000000000g",
                "not-a-uuid",
                "00000000-0000-0000-0000-000000000001,00000000-0000-0000-0000-000000000002",
                "",
                "   ",
            })
    void refusesAnythingButACanonicalUuid(String value) {
        request.addHeader("X-Dev-User-Id", value);

        assertThatThrownBy(provider::currentUserId)
                .isInstanceOf(MalformedRequestException.class)
                .hasMessageContaining("X-Dev-User-Id");
    }

    @Test
    void anEmptyHeaderIsNotTheSameAsNoHeader() {
        // The easiest mistake to make here: treating "" as absent. That would give the dev user's data to a client
        // that meant to send somebody else's id.
        request.addHeader("X-Dev-User-Id", "");

        assertThatThrownBy(provider::currentUserId).isInstanceOf(MalformedRequestException.class);
    }

    @Test
    void refusesTheHeaderSentTwice() {
        request.addHeader("X-Dev-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-Dev-User-Id", UUID.randomUUID().toString());

        assertThatThrownBy(provider::currentUserId)
                .isInstanceOf(MalformedRequestException.class)
                .hasMessageContaining("once");
    }

    @Test
    void theErrorMessageDoesNotRepeatTheValueItRefused() {
        request.addHeader("X-Dev-User-Id", "<script>alert(1)</script>");

        assertThatThrownBy(provider::currentUserId)
                .isInstanceOf(MalformedRequestException.class)
                .message()
                .doesNotContain("script")
                .doesNotContain("alert");
    }
}
