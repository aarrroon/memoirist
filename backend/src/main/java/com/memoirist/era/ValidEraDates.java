package com.memoirist.era;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The dates of an Era make sense together: each lies between {@code 1900-01-01} (the earliest memory date an entry
 * accepts) and {@code 9999-12-31}, and the end is not before the start. A missing date passes, as in every Jakarta
 * constraint: {@code @NotNull} on {@code startDate} says it is required.
 *
 * <p>This sits on the <em>class</em>, because the second rule compares two fields. A class-level violation would
 * normally belong to no field, so it would appear only in {@code detail}. {@link EraDatesValidator} attaches each
 * message to the field it is about instead, so the response says {@code fieldErrors.endDate}.
 */
@Documented
@Constraint(validatedBy = EraDatesValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEraDates {

    /** Only a fallback: the validator writes its own messages, one per field. */
    String message() default "must be a valid date range";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
