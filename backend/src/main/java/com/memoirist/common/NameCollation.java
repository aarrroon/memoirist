package com.memoirist.common;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * How the API orders user-chosen names (templates, tags): by base letter, so {@code apple}, {@code Apple} and
 * {@code Äpple} compare as equal and {@code Apple} sorts before {@code Banana}. Java's default string order puts every
 * capital before every small letter.
 *
 * <p>The sort happens in Java, with {@code Locale.ROOT} rather than the JVM's locale, because a database sorts by the
 * collation it was created with, which differs between a laptop, a CI container and a hosted database. The same call
 * gives the same order everywhere.
 */
public final class NameCollation {

    private static final Collator BASE_LETTERS = Collator.getInstance(Locale.ROOT);

    static {
        BASE_LETTERS.setStrength(Collator.PRIMARY);
    }

    /**
     * Equal for names that differ only in case or accents. A caller adds the tie-breakers it needs, such as the exact
     * spelling and then the age of the row. {@code Collator.compare} is synchronized, so the one shared instance is safe
     * to use from many requests.
     */
    public static final Comparator<String> BASE_LETTERS_ORDER = BASE_LETTERS::compare;

    private NameCollation() {}
}
