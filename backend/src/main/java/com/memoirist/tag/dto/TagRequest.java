package com.memoirist.tag.dto;

import com.memoirist.tag.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/v1/tags} and {@code PUT /api/v1/tags/{tagId}} (API_CONTRACT, Tags). Both carry the same
 * one field, so they share the record: a tag has no client-supplied id, and a {@code PUT} replaces the only thing in it
 * that can change.
 *
 * <p>The name is cleaned in the constructor, before it is validated: it loses the spaces at both ends, so a name of
 * only spaces is empty and fails its length rule. A missing name stays {@code null} and is reported as
 * {@code must not be null}. Other fields in the body ({@code entryCount}, {@code userId}) are ignored.
 *
 * @param name 1 to {@value Tag#MAX_NAME_LENGTH} characters once stripped
 */
public record TagRequest(
        @NotNull @Size(min = 1, max = Tag.MAX_NAME_LENGTH) String name) {

    public TagRequest {
        name = name == null ? null : name.strip();
    }
}
