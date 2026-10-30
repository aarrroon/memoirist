package com.memoirist.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * The whole application started with a profile that is not {@code local} or {@code test}: there is no dev provider, so
 * {@code X-Dev-User-Id} can't pick a user, and the dev user was never seeded either (D-11, "so it never reaches
 * production").
 *
 * <p>This is the slow version of {@link CurrentUserProfileTest}: a full context and a Postgres container of its own.
 * It earns that cost by checking the real app, which would catch a second place that creates the provider (a stray
 * {@code @Bean} method, say) that a one-class test can't see. It doesn't use {@code @IntegrationTest}, which fixes the
 * {@code test} profile, and it supplies the storage settings that {@code application-test.yml} would have.
 */
@SpringBootTest(
        properties = {
            "storage.endpoint=http://storage.invalid:8333",
            "storage.public-endpoint=http://storage.invalid:8333",
            "storage.region=us-east-1",
            "storage.bucket=memoirist-media",
            "storage.access-key=test-access-key",
            "storage.secret-key=test-secret-key",
            "storage.path-style=true",
            "storage.presign-ttl=PT60M",
        })
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class DevProviderAbsentInProdTest {

    private static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    ApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    @Test
    void theApplicationStartsWithoutAnyCurrentUserProvider() {
        assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("prod");
        assertThat(context.getBeanNamesForType(DevHeaderCurrentUserProvider.class))
                .isEmpty();
        // Phase 2 adds the JWT provider. Until then nothing in prod can say who the user is.
        assertThat(context.getBeanNamesForType(CurrentUserProvider.class)).isEmpty();
    }

    @Test
    void theDevUserIsNotInTheDatabase() {
        Long devUsers = jdbc.sql("SELECT count(*) FROM users WHERE id = :id")
                .param("id", DEV_USER_ID)
                .query(Long.class)
                .single();

        assertThat(devUsers).isZero();
    }
}
