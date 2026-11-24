package com.memoirist.template.dto;

import java.util.List;

/** How the text fields of a Template request are cleaned, shared by the create and update records. */
final class TemplateText {

    private TemplateText() {}

    /** Stripped of spaces at both ends. {@code null} stays {@code null}, so validation can report it. */
    static String name(String name) {
        return name == null ? null : name.strip();
    }

    // TODO(human) PRACTICE: Write subheadings(): strip every item and hand back a list that can't be changed later.
    //   A missing list and a missing item (null) must both come out as null, not as an exception.
    // Hint: This runs in a record's constructor, before validation, so a crash here is a 500 instead of a 400 with
    //   fieldErrors (API_CONTRACT, Errors). Try List.copyOf on a list that holds a null, then read what
    //   Stream.toList() allows that it doesn't. Which exception would the client have seen?
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    static List<String> subheadings(List<String> subheadings) {
        if (subheadings == null) {
            return null;
        }
        return subheadings.stream()
                .map(item -> item == null ? null : item.strip())
                .toList();
    }
    // └── END ANSWER ▲ ──
}
