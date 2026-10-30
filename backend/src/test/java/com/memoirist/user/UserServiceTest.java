package com.memoirist.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.memoirist.Fixtures;
import com.memoirist.common.MalformedRequestException;
import com.memoirist.common.NotFoundException;
import com.memoirist.user.dto.UserResponse;
import com.memoirist.user.dto.UserUpdateRequest;
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
    private final UserService service = new UserService(users, provider);

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

        UserResponse response = service.update(new UserUpdateRequest("Sam", null));

        assertThat(response).isEqualTo(new UserResponse(user.getId(), user.getEmail(), "Sam", "Australia/Melbourne"));
        assertThat(user.getTimezone()).isEqualTo("Australia/Melbourne");
    }

    @Test
    void updateCanChangeJustTheZone() {
        User user = aUser();

        UserResponse response = service.update(new UserUpdateRequest(null, "America/New_York"));

        assertThat(response.timezone()).isEqualTo("America/New_York");
        assertThat(response.displayName()).isEqualTo("Test User");
        assertThat(user.getDisplayName()).isEqualTo("Test User");
    }

    @Test
    void anEmptyUpdateChangesNothing() {
        User user = aUser();

        UserResponse response = service.update(new UserUpdateRequest(null, null));

        assertThat(response).isEqualTo(UserMapper.toResponse(user));
        assertThat(user.getDisplayName()).isEqualTo("Test User");
    }

    @Test
    void updateNeverCallsSave() {
        // The user is managed inside the service's transaction, so Hibernate writes the change at commit. A repository
        // call here would be redundant.
        User user = aUser();

        service.update(new UserUpdateRequest("Sam", "UTC"));

        // findById is the only call; verifyNoMoreInteractions fails on a save() that was not stubbed or verified.
        org.mockito.Mockito.verify(users).findById(user.getId());
        verifyNoMoreInteractions(users);
    }
}
