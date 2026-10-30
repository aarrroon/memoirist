package com.memoirist.common;

import java.util.Map;

/**
 * A business rule on the input failed, such as an entry date in the future. It becomes {@code 400 validation-failed}
 * (API_CONTRACT, Errors), with {@code fieldErrors} when the rule is about one field.
 *
 * <p>Rules that bean validation can state on the request record ({@code @NotBlank}, {@code @Size}) don't need this:
 * Spring raises its own exception for them and the advice turns it into the same response. Use this one for rules
 * that need the clock, the database or the user (the entry-date rule needs "today" in the user's zone).
 *
 * <p>Write the messages for the caller, and never put the rejected value in them (the value is untrusted input).
 */
public class ValidationFailedException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    /** A rule that isn't about one field. The response has no {@code fieldErrors}. */
    public ValidationFailedException(String message) {
        super(message);
        this.fieldErrors = Map.of();
    }

    /** A rule about one field: {@code new ValidationFailedException("entryDate", "must not be after today")}. */
    public ValidationFailedException(String field, String message) {
        super(field + " " + message);
        this.fieldErrors = Map.of(field, message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
