package com.memoirist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.memoirist.storage.StorageProperties;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.MultipartConfigElement;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.TimeZone;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.util.unit.DataSize;

/**
 * Checks the configuration the running app actually ended up with, in the {@code test} profile (task 2.2).
 *
 * <p>A mistyped key in a YAML file is silently ignored, and the default quietly stays in force. So these tests ask the
 * beans that use each setting, rather than reading the files back.
 */
@IntegrationTest
class ConfigurationTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    Clock clock;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    MultipartConfigElement multipartConfig;

    @Autowired
    Flyway flyway;

    @Autowired
    StorageProperties storage;

    @Test
    void jvmRunsInUtcAndTheClockBeanIsTheSystemClockInUtc() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        // A real, ticking clock: tests that need a fixed time replace this bean.
        assertThat(clock.instant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void hibernateOnlyValidatesTheSchemaAndUsesUtcForJdbc() {
        assertThat(entityManagerFactory.getProperties())
                .containsEntry("hibernate.hbm2ddl.auto", "validate")
                .containsEntry("hibernate.jdbc.time_zone", "UTC");
    }

    @Test
    void openSessionInViewIsOff() {
        // With spring.jpa.open-in-view on (Spring Boot's default), this interceptor keeps a database session open
        // until the HTTP response is written.
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class))
                .isEmpty();
    }

    @Test
    void uploadsMayBe25MbPerFileAnd30MbPerRequest() {
        assertThat(multipartConfig.getMaxFileSize())
                .isEqualTo(DataSize.ofMegabytes(25).toBytes());
        assertThat(multipartConfig.getMaxRequestSize())
                .isEqualTo(DataSize.ofMegabytes(30).toBytes());
    }

    @Test
    void flywayLoadsTheSchemaAndTheDevUserSeed() {
        assertThat(Arrays.stream(flyway.getConfiguration().getLocations()).map(Location::getDescriptor))
                .containsExactly("classpath:db/migration", "classpath:db/seed-local");
    }

    @Test
    void storageSettingsComeFromTheTestProfile() {
        assertThat(storage.endpoint()).isEqualTo(URI.create("http://storage.invalid:8333"));
        assertThat(storage.publicEndpoint()).isEqualTo(URI.create("http://storage.invalid:8333"));
        assertThat(storage.bucket()).isEqualTo("memoirist-media");
        assertThat(storage.pathStyle()).isTrue();
        assertThat(storage.presignTtl()).isEqualTo(Duration.ofMinutes(60));
    }
}
