package com.memoirist;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts Postgres in Docker for integration tests (D-14). Import it with
 * {@code @Import(TestcontainersConfiguration.class)}.
 *
 * <p>The container is a bean, so it lives as long as the Spring test context. Spring caches contexts,
 * so test classes with the same configuration share one container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** The image Docker Compose runs (D-04), so tests and local runs use the same Postgres. */
    public static final String POSTGRES_IMAGE = "pgvector/pgvector:pg17";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
