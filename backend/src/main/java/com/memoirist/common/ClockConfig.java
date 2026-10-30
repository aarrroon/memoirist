package com.memoirist.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one source of "now" (D-07). Code injects this {@link Clock} and calls {@code Instant.now(clock)} or
 * {@code LocalDate.now(clock.withZone(userZone))}, never {@code Instant.now()}, so a test can swap in a fixed clock
 * to check a seal or a date on the edge of a day.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * The system clock in UTC. Its zone is fixed rather than taken from the JVM, so a mistaken
     * {@code LocalDate.now(clock)} gives the UTC date on every machine instead of the host's.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
