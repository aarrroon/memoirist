package com.memoirist.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Collects the validation messages of one request into the flat {@code fieldErrors} object of API_CONTRACT (Errors),
 * {@code { "entryDate": "must not be after today" }}, and into the one-sentence {@code detail} that goes with it.
 *
 * <p>Bean validation reports every failed constraint, and one field can fail several ({@code @NotBlank} and
 * {@code @Size} on the same title). The contract allows one string per field, so the messages of a field are joined.
 * Only messages are collected here, never the rejected values: a value is untrusted input, and echoing it back is how
 * reflected-content bugs start.
 */
final class FieldErrors {

    private static final String SEPARATOR = "; ";

    /** Messages that belong to no single field, such as a rule that compares two fields. */
    private final SortedSet<String> general = new TreeSet<>();

    // Sorted on purpose: Spring reports errors in no promised order, and a response whose JSON order changes between
    // runs makes tests flaky and diffs noisy.
    private final SortedMap<String, SortedSet<String>> byField = new TreeMap<>();

    // TODO(human) PRACTICE: Fill in add() and asMap() so one field can fail several rules and the response still has
    //   one string per field, the same on every run. Then decide what a null message becomes.
    // Hint: API_CONTRACT (Errors) shows fieldErrors as { field: message }. Think about what you want when two
    //   constraints fail on one field, when the same message arrives twice, and when Spring gives no message at all
    //   (getDefaultMessage() may return null). Map.merge or computeIfAbsent plus a sorted set are the tools to try.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    void add(String field, String message) {
        byField.computeIfAbsent(field, key -> new TreeSet<>()).add(message == null ? "is invalid" : message);
    }

    Map<String, String> asMap() {
        Map<String, String> flat = new LinkedHashMap<>();
        byField.forEach((field, messages) -> flat.put(field, String.join(SEPARATOR, messages)));
        return flat;
    }
    // └── END ANSWER ▲ ──

    void addGeneral(String message) {
        general.add(message == null ? "is invalid" : message);
    }

    /** True when no message names a field, so the response has no {@code fieldErrors}. */
    boolean hasNoFields() {
        return byField.isEmpty();
    }

    /** The {@code detail}: {@code "entryDate must not be after today; title size must be between 1 and 150"}. */
    String summary() {
        List<String> parts = new ArrayList<>();
        byField.forEach((field, messages) -> messages.forEach(message -> parts.add(field + " " + message)));
        parts.addAll(general);
        return String.join(SEPARATOR, parts);
    }
}
