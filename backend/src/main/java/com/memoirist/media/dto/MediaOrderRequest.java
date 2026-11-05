package com.memoirist.media.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The new carousel order of an entry (API_CONTRACT, Media): every one of its photos and voice notes, once, first to
 * last. Which ids those are can't be said with annotations, because it depends on the entry, so the service checks it.
 *
 * @param mediaIds at most 20, the most an entry can hold (D-17); this bound keeps a huge list from being walked at all
 */
public record MediaOrderRequest(@NotNull @Size(max = 20) List<@NotNull UUID> mediaIds) {}
