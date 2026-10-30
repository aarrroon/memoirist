package com.memoirist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplication.AbandonedRunException;
import org.springframework.boot.SpringApplicationHook;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Proves that {@link MemoiristApplication#main} makes UTC the JVM's default zone before Spring starts (D-07).
 *
 * <p>Gradle starts the test JVM in UTC (build.gradle.kts), and CI machines usually run in UTC too, so asserting
 * {@code TimeZone.getDefault()} on its own would pass even without the line in {@code main}. This test starts from
 * Melbourne's zone instead, runs the real {@code main}, and stops Spring as soon as it has read its configuration:
 * no beans, no database, no web server.
 *
 * <p>{@code @Isolated}: the test changes the JVM's default zone for a moment, so if tests ever run in parallel, JUnit
 * runs this one on its own.
 */
@Isolated
class MemoiristApplicationTest {

    @Test
    void mainSwitchesTheJvmToUtcBeforeSpringStarts() {
        TimeZone original = TimeZone.getDefault();
        AtomicReference<TimeZone> zoneWhenSpringStarted = new AtomicReference<>();
        SpringApplicationHook recordZoneThenStop = application -> new SpringApplicationRunListener() {
            @Override
            public void starting(ConfigurableBootstrapContext bootstrapContext) {
                // The first thing SpringApplication.run() does.
                zoneWhenSpringStarted.set(TimeZone.getDefault());
            }

            @Override
            public void environmentPrepared(
                    ConfigurableBootstrapContext bootstrapContext, ConfigurableEnvironment environment) {
                // Stop here, once Spring Boot has set up logging, rather than in starting(). Boot switches logging off
                // while it starts, so stopping in starting() would leave it off: log lines from later tests would
                // vanish until another Spring Boot app started in this JVM.
                throw new AbandonedRunException();
            }
        };
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Australia/Melbourne"));

            assertThatExceptionOfType(AbandonedRunException.class)
                    .isThrownBy(() -> SpringApplication.withHook(
                            recordZoneThenStop, () -> MemoiristApplication.main(new String[0])));

            assertThat(zoneWhenSpringStarted.get().getID()).isEqualTo("UTC");
        } finally {
            // The default zone belongs to the whole JVM, which every other test shares.
            TimeZone.setDefault(original);
        }
    }
}
