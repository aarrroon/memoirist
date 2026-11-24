package com.memoirist.user;

import com.memoirist.user.dto.UserResponse;
import com.memoirist.user.dto.UserUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/users/me}: the current user's profile (API_CONTRACT, Users). HTTP only: the service finds the user,
 * and no id comes from the request (D-11).
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
class UserController {

    private final UserService users;

    UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    @Operation(summary = "The current user")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "400", description = "malformed-request: the X-Dev-User-Id header is not a UUID")
    @ApiResponse(responseCode = "404", description = "not-found: the header names a user who doesn't exist")
    UserResponse me() {
        return users.get();
    }

    @PatchMapping("/me")
    @Operation(summary = "Change the current user's display name or time zone")
    @ApiResponse(responseCode = "200", description = "The user as saved")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors (a blank or over-long displayName, an unknown timezone),"
                    + " or malformed-request (unreadable JSON, a bad X-Dev-User-Id)")
    @ApiResponse(responseCode = "404", description = "not-found: the header names a user who doesn't exist")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    UserResponse update(@Valid @RequestBody UserUpdateRequest request) {
        return users.update(request);
    }
}
