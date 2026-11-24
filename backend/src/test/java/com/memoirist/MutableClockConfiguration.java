package com.memoirist;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the app's clock with a {@link MutableClock} in an integration test. Import it next to
 * {@code @IntegrationTest}: {@code @Import(MutableClockConfiguration.class)}.
 *
 * <p>The app's own {@code Clock} bean stays in the context; this one is {@code @Primary}, so everything that asks for
 * a {@code Clock} gets the movable one (the services, and the error advice that stamps its timestamps), and a test
 * asks for the {@code MutableClock} itself to move it. Because the configuration differs from plain
 * {@code @IntegrationTest}, test classes that use it share one context and one Postgres container of their own.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfiguration {

    /** Saturday 2026-10-31 at 12:00 in Melbourne (UTC+11 in summer): the week that starts on Monday 2026-10-26. */
    public static final Instant START = Instant.parse("2026-10-31T01:00:00Z");

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(START);
    }
}
