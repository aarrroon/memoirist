package com.memoirist.user.dto;

import java.util.UUID;

/**
 * What {@code GET} and {@code PATCH /api/v1/users/me} answer (API_CONTRACT, Users).
 *
 * <p>Only what the contract lists: no {@code createdAt}, no {@code updatedAt}. The {@code birthday} joins in task 3.13.
 */
public record UserResponse(UUID id, String email, String displayName, String timezone) {}
