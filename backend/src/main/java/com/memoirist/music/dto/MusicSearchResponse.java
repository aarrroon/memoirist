package com.memoirist.music.dto;

import java.util.List;

/** The answer to a search: at most 10 results, never {@code null}. */
public record MusicSearchResponse(List<MusicResult> items) {}
