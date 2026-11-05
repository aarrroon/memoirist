package com.memoirist.era.dto;

/** How the text fields of an Era request are cleaned, shared by the create and update records. */
final class EraText {

    private EraText() {}

    /** Stripped; {@code null} when there is nothing left, so "no description" has one spelling. */
    static String description(String description) {
        if (description == null) {
            return null;
        }
        String stripped = description.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
