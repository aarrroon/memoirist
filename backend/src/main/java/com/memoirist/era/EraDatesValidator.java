package com.memoirist.era;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;

/**
 * The rule behind {@link ValidEraDates}. Hibernate Validator creates one instance and calls {@link #isValid} from many
 * request threads at once, so it keeps no state that changes.
 */
public class EraDatesValidator implements ConstraintValidator<ValidEraDates, EraDates> {

    // TODO(human) PRACTICE: Write isValid() for the two dates of an Era: each date, if present, lies between
    //   1900-01-01 and 9999-12-31, and the end is not before the start (a one-day Era has the same start and end).
    //   The response must say WHICH field is wrong (fieldErrors.startDate or fieldErrors.endDate), although the
    //   constraint sits on the whole record. Work out how to turn off the single class-level violation Jakarta would
    //   report, and how to add one per field instead. Then decide what to do when both rules fail at once.
    // Hint: ConstraintValidatorContext.disableDefaultConstraintViolation(), then
    //   buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation(). Null dates pass
    //   here (@NotNull on startDate says it is required). Messages must not repeat the value (it is untrusted input),
    //   and "{" or "$" in a message template is read as an expression, so keep the messages plain. API_CONTRACT (Eras).
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    static final LocalDate LATEST = LocalDate.of(9999, 12, 31);
    static final String OUT_OF_RANGE = "must be between 1900-01-01 and 9999-12-31";
    static final String BEFORE_START = "must not be before startDate";

    @Override
    public boolean isValid(EraDates dates, ConstraintValidatorContext context) {
        if (dates == null) {
            return true;
        }
        LocalDate start = dates.startDate();
        LocalDate end = dates.endDate();
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if (outOfRange(start)) {
            valid = report(context, "startDate", OUT_OF_RANGE);
        }
        if (outOfRange(end)) {
            valid = report(context, "endDate", OUT_OF_RANGE);
        } else if (start != null && end != null && !outOfRange(start) && end.isBefore(start)) {
            valid = report(context, "endDate", BEFORE_START);
        }
        return valid;
    }

    private static boolean outOfRange(LocalDate date) {
        return date != null && (date.isBefore(EARLIEST) || date.isAfter(LATEST));
    }

    private static boolean report(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
        return false;
    }
    // └── END ANSWER ▲ ──
}
