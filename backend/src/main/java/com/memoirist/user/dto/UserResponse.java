package com.memoirist.user.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * What {@code GET} and {@code PATCH /api/v1/users/me} answer (API_CONTRACT, Users).
 *
 * <p>Only what the contract lists: no {@code createdAt}, no {@code updatedAt}.
 *
 * @param birthday the day the user was born, or {@code null} when they haven't set one (D-33); it is always in the
 *     answer, as {@code null}, so a client can tell "not set" from "not sent"
 */
public record UserResponse(UUID id, String email, String displayName, String timezone, LocalDate birthday) {}
