package com.memoirist.music;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The text is a Spotify track, album or playlist URI such as {@code spotify:track:4uLU6hMCjMI75M1A2tKUQC} (D-35).
 * {@code null} passes, as in every Jakarta constraint, so "no soundtrack" is valid; the empty string is not.
 *
 * <p>Put it on the {@code soundtrack} of a request and a wrong value comes back as {@code 400 validation-failed} with
 * {@code fieldErrors.soundtrack}. {@link SpotifyUriValidator} holds the rule, and {@link Soundtrack} is the same rule
 * for plain code.
 */
@Documented
@Constraint(validatedBy = SpotifyUriValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface SpotifyUri {

    /** The message in {@code fieldErrors}. Plain text on purpose: Hibernate Validator would read braces in it. */
    String message() default
            "must be a Spotify track, album or playlist URI, such as spotify:track:4uLU6hMCjMI75M1A2tKUQC";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
