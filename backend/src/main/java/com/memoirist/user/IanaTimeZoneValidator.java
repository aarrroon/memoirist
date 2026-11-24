package com.memoirist.user;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The rule behind {@link IanaTimeZone}. Hibernate Validator creates one instance and calls {@link #isValid} for every
 * value, from many request threads at once, so it keeps no state that changes.
 */
public class IanaTimeZoneValidator implements ConstraintValidator<IanaTimeZone, String> {

    // TODO(human) PRACTICE: Write isValid(): the text is a zone name that the app can use for "today" and for week
    //   boundaries (D-07), or it is null. Try ZoneId.of on "UTC+10", "+10:00", "Z", "australia/melbourne" and
    //   "Australia/Melbourne " first, then decide which of those you accept and how to look a name up without an
    //   exception. Also decide what to do about ZoneId.getAvailableZoneIds(), which builds a new set on every call.
    // Hint: D-07 and API_CONTRACT say "IANA zone". The browser shows every time with Intl.DateTimeFormat, which
    //   rejects "UTC+10", and the JDK's list still holds obsolete "SystemV/..." names that are not IANA. Null must be
    //   valid (null means "no change" in a PATCH). The message must not repeat the value (it is untrusted input).
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    private static final Set<String> ZONES = ZoneId.getAvailableZoneIds().stream()
            .filter(id -> !id.startsWith("SystemV/"))
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || ZONES.contains(value);
    }
    // └── END ANSWER ▲ ──
}
