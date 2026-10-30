package com.memoirist.common;

/**
 * Someone tried to change a sealed Loot Drop's content, date, template, tags, soundtrack, reply target or media, or to
 * move its unlock date earlier (D-10). It becomes {@code 409 entry-sealed} (API_CONTRACT, Errors).
 *
 * <p>The message goes to the client. It may say the entry is sealed (the unlock date is not private) but must never
 * repeat what is inside the entry.
 */
public class EntrySealedException extends RuntimeException {

    public EntrySealedException(String message) {
        super(message);
    }
}
