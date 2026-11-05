package com.memoirist.music;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** The rule behind {@link SpotifyUri}: null is fine, anything else must pass {@link Soundtrack#isValid}. */
public class SpotifyUriValidator implements ConstraintValidator<SpotifyUri, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || Soundtrack.isValid(value);
    }
}
