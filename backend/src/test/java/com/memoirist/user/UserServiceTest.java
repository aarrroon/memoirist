package com.memoirist.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.memoirist.Fixtures;
import com.memoirist.common.MalformedRequestException;
import com.memoirist.common.NotFoundException;
import com.memoirist.common.ValidationFailedException;
import com.memoirist.user.dto.BirthdayChange;
import com.memoirist.user.dto.UserResponse;
import com.memoirist.user.dto.UserUpdateRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link UserService} with its two collaborators replaced by mocks: no Spring, no database. These tests are about the
 * decisions in the service. Whether the SQL really runs is {@link UserEndpointTest}'s job.
 */
class UserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserProvider provider = mock(CurrentUserProvider.class);
    /** Saturday 2026-10-31, 01:00 UTC: 12:00 in Melbourne (UTC+11), but still Friday 30 October in Los Angeles. */
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T01:00:00Z"), ZoneOffset.UTC);

    private final UserService service = new UserService(users, provider, clock);

    // The decision of task 3.2: a user who isn't there

    @Test
    void aWellFormedIdThatNamesNobodyIsNotFound() {
        UUID nobody = UUID.randomUUID();
        when(provider.currentUserId()).thenReturn(nobody);
        when(users.findById(nobody)).thenReturn(Optional.empty());

        assertThatThrownBy(service::currentUser)
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found.")
                // The id came from the client's header. The message goes into the response, so it must not repeat it.
                .hasMessageNotContaining(nobody.toString());
    }

    @Test
    void currentUserLooksUpExactlyTheIdTheProviderGave() {
        User someone = Fixtures.user();
        when(provider.currentUserId()).thenReturn(someone.getId());
        when(users.findById(someone.getId())).thenReturn(Optional.of(someone));

        assertThat(service.currentUser()).isSameAs(someone);
    }

    @Test
    void aBadHeaderIsTheProvidersErrorAndNoLookupHappens() {
        when(provider.currentUserId()).thenThrow(new MalformedRequestException("bad header"));

        assertThatThrownBy(service::currentUser).isInstanceOf(MalformedRequestException.class);
        verifyNoMoreInteractions(users);
    }

    // PATCH semantics

    private User aUser() {
        User user = Fixtures.user();
        when(provider.currentUserId()).thenReturn(user.getId());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void updateChangesOnlyTheFieldsThatArePresent() {
        User user = aUser();

        UserResponse response = service.update(new UserUpdateRequest("Sam", null, null));

        assertThat(response)
                .isEqualTo(new UserResponse(user.getId(), user.getEmail(), "Sam", "Australia/Melbourne", null));
        assertThat(user.getTimezone()).isEqualTo("Australia/Melbourne");
    }

    @Test
    void updateCanChangeJustTheZone() {
        User user = aUser();

        UserResponse response = service.update(new UserUpdateRequest(null, "America/New_York", null));

        assertThat(response.timezone()).isEqualTo("America/New_York");
        assertThat(response.displayName()).isEqualTo("Test User");
        assertThat(user.getDisplayName()).isEqualTo("Test User");
    }

    @Test
    void anEmptyUpdateChangesNothing() {
        User user = aUser();

        UserResponse response = service.update(new UserUpdateRequest(null, null, null));

        assertThat(response).isEqualTo(UserMapper.toResponse(user));
        assertThat(user.getDisplayName()).isEqualTo("Test User");
    }

    @Test
    void updateNeverCallsSave() {
        // The user is managed inside the service's transaction, so Hibernate writes the change at commit. A repository
        // call here would be redundant.
        User user = aUser();

        service.update(new UserUpdateRequest("Sam", "UTC", null));

        // findById is the only call; verifyNoMoreInteractions fails on a save() that was not stubbed or verified.
        org.mockito.Mockito.verify(users).findById(user.getId());
        verifyNoMoreInteractions(users);
    }

    // The birthday (task 3.13, D-33)

    private static UserUpdateRequest birthdayOnly(BirthdayChange birthday) {
        return new UserUpdateRequest(null, null, birthday);
    }

    @Test
    void aMissingBirthdayLeavesTheStoredOneAlone() {
        User user = aUser();
        user.setBirthday(LocalDate.of(1990, 5, 17));

        UserResponse response = service.update(new UserUpdateRequest("Sam", null, null));

        assertThat(response.birthday()).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(user.getBirthday()).isEqualTo(LocalDate.of(1990, 5, 17));
    }

    @Test
    void aNullBirthdayRemovesTheStoredOne() {
        User user = aUser();
        user.setBirthday(LocalDate.of(1990, 5, 17));

        UserResponse response = service.update(birthdayOnly(BirthdayChange.REMOVE));

        assertThat(response.birthday()).isNull();
        assertThat(user.getBirthday()).isNull();
    }

    @Test
    void aDateSetsTheBirthday() {
        User user = aUser();

        UserResponse response = service.update(birthdayOnly(BirthdayChange.to(LocalDate.of(1998, 2, 28))));

        assertThat(response.birthday()).isEqualTo(LocalDate.of(1998, 2, 28));
        assertThat(user.getBirthday()).isEqualTo(LocalDate.of(1998, 2, 28));
    }

    @Test
    void todayIsAllowedAndTomorrowIsNot() {
        User user = aUser(); // Melbourne: today is Saturday 2026-10-31

        assertThat(service.update(birthdayOnly(BirthdayChange.to(LocalDate.of(2026, 10, 31))))
                        .birthday())
                .isEqualTo(LocalDate.of(2026, 10, 31));

        assertThatThrownBy(() -> service.update(birthdayOnly(BirthdayChange.to(LocalDate.of(2026, 11, 1)))))
                .isInstanceOfSatisfying(
                        ValidationFailedException.class,
                        e -> assertThat(e.getFieldErrors())
                                .containsOnly(
                                        org.assertj.core.api.Assertions.entry("birthday", "must not be after today")));
        assertThat(user.getBirthday()).as("the refused date was not stored").isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void todayIsTheUsersTodayNotTheServers() {
        User user = aUser();
        user.setTimezone("America/Los_Angeles"); // still Friday 2026-10-30 there

        assertThatThrownBy(() -> service.update(birthdayOnly(BirthdayChange.to(LocalDate.of(2026, 10, 31)))))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(service.update(birthdayOnly(BirthdayChange.to(LocalDate.of(2026, 10, 30))))
                        .birthday())
                .isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void aZoneChangedInTheSameRequestDecidesTheDay() {
        User user = aUser();
        user.setTimezone("America/Los_Angeles");

        // Moving to Melbourne and giving the Melbourne-today birthday in one request: judged in the new zone.
        UserResponse response = service.update(
                new UserUpdateRequest(null, "Australia/Melbourne", BirthdayChange.to(LocalDate.of(2026, 10, 31))));

        assertThat(response.birthday()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(user.getTimezone()).isEqualTo("Australia/Melbourne");

        // And the other way: moving to Los Angeles makes that same date a future one.
        assertThatThrownBy(() -> service.update(new UserUpdateRequest(
                        null, "America/Los_Angeles", BirthdayChange.to(LocalDate.of(2026, 10, 31)))))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void aBadBirthdayChangesNothingElseInTheSameRequest() {
        User user = aUser();

        assertThatThrownBy(() -> service.update(new UserUpdateRequest(
                        "Renamed", "America/New_York", BirthdayChange.to(LocalDate.of(2999, 1, 1)))))
                .isInstanceOf(ValidationFailedException.class);

        assertThat(user.getDisplayName()).isEqualTo("Test User");
        assertThat(user.getTimezone()).isEqualTo("Australia/Melbourne");
        assertThat(user.getBirthday()).isNull();
    }

    @Test
    void theFirstDayOf1900IsTheEarliestBirthday() {
        User user = aUser();

        assertThat(service.update(birthdayOnly(BirthdayChange.to(LocalDate.of(1900, 1, 1))))
                        .birthday())
                .isEqualTo(LocalDate.of(1900, 1, 1));

        assertThatThrownBy(() -> service.update(birthdayOnly(BirthdayChange.to(LocalDate.of(1899, 12, 31)))))
                .isInstanceOfSatisfying(
                        ValidationFailedException.class,
                        e -> assertThat(e.getFieldErrors())
                                .containsOnly(org.assertj.core.api.Assertions.entry(
                                        "birthday", "must not be before 1900-01-01")));
        assertThat(user.getBirthday()).isEqualTo(LocalDate.of(1900, 1, 1));
    }
}
