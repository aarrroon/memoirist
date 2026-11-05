package com.memoirist.user;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The text is an IANA time zone name such as {@code Australia/Melbourne} (D-07). {@code null} passes, as in every
 * Jakarta constraint, so a PATCH that leaves the zone alone is valid; add {@code @NotNull} where it is required.
 *
 * <p>Put it on a request field and a wrong zone comes back as {@code 400 validation-failed} with the field named in
 * {@code fieldErrors}, in the same response as every other field that failed. {@link IanaTimeZoneValidator} holds the
 * rule.
 */
@Documented
@Constraint(validatedBy = IanaTimeZoneValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface IanaTimeZone {

    /** The message in {@code fieldErrors}. It is plain text on purpose: Hibernate Validator would read braces in it. */
    String message() default "must be an IANA time zone, such as Australia/Melbourne";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
