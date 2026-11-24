package com.memoirist.entry.dto;

import tools.jackson.databind.JsonNode;

/** How the title and caption of an entry request are cleaned, shared by the create and update records. */
final class EntryText {

    private EntryText() {}

    /**
     * The title without the spaces at both ends, so a title of only spaces is empty and fails its length rule. A
     * missing title stays {@code null}: it is optional (D-24), and an empty one is not a way to say "no title".
     */
    static String title(String title) {
        return title == null ? null : title.strip();
    }

    /** A caption that is missing and one that is JSON {@code null} both mean "no caption" (D-05). */
    static JsonNode content(JsonNode contentJson) {
        return contentJson == null || contentJson.isNull() ? null : contentJson;
    }
}
