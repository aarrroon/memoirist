package com.memoirist.user;

import com.memoirist.user.dto.UserResponse;

/** User to DTO, written by hand (no MapStruct, backend rules). Call it inside the service's transaction. */
final class UserMapper {

    private UserMapper() {}

    static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getTimezone());
    }
}
