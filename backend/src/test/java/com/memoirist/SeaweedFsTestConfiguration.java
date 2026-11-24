package com.memoirist;

import com.memoirist.storage.BucketBootstrap;
import com.memoirist.storage.StorageProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Starts SeaweedFS in Docker for tests that store files (D-03, task 3.1), the way {@link TestcontainersConfiguration}
 * starts Postgres. Add it to an integration test with {@code @IntegrationTest @Import(SeaweedFsTestConfiguration.class)}.
 *
 * <p>The container is configured like the {@code seaweedfs} service in {@code docker-compose.yml}: the same image tag,
 * the same {@code server -s3 ...} command, and the S3 keys given as {@code AWS_ACCESS_KEY_ID} and
 * {@code AWS_SECRET_ACCESS_KEY}. {@code SeaweedFsComposeTest} fails when the two drift apart. The keys are the ones
 * in {@code application-test.yml} ({@code storage.access-key} and {@code storage.secret-key}), so the app's client and
 * the container agree without a second copy.
 *
 * <p>Docker publishes the container's S3 port on a random free port, so the endpoints are not known until it has
 * started. A {@link DynamicPropertyRegistrar} bean takes the container as a parameter, which makes Spring start the
 * container first, and then sets {@code storage.endpoint} and {@code storage.public-endpoint} over the placeholders in
 * {@code application-test.yml}. The two differ on purpose ({@code localhost} against {@code 127.0.0.1}), so a test can
 * tell which one signed a URL.
 *
 * <p>It also adds the {@link BucketBootstrap} bean, which only the {@code local} profile has in the app, so the bucket
 * exists before any test runs. Spring caches a test context by its configuration: every class that imports this
 * configuration shares one context, one Postgres and one SeaweedFS.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SeaweedFsTestConfiguration {

    /**
     * The image Docker Compose runs, pinned (D-03, Version). {@code SeaweedFsComposeTest} compares it with
     * {@code docker-compose.yml}.
     */
    public static final String SEAWEEDFS_IMAGE = "chrislusf/seaweedfs:4.48";

    /** The arguments Docker Compose gives the image (the {@code command} of its {@code seaweedfs} service). */
    public static final List<String> SEAWEEDFS_COMMAND = List.of(
            "server",
            "-s3",
            "-s3.iam=false",
            "-s3.port.iceberg=0",
            "-s3.port.lance=0",
            "-ip.bind=0.0.0.0",
            "-master.peers=none",
            "-master.telemetry=false",
            "-volume.preStopSeconds=1");

    private static final int S3_PORT = 8333;

    @Bean
    SeaweedFsContainer seaweedFsContainer(Environment environment) {
        return new SeaweedFsContainer(
                environment.getRequiredProperty("storage.access-key"),
                environment.getRequiredProperty("storage.secret-key"));
    }

    @Bean
    DynamicPropertyRegistrar seaweedFsProperties(SeaweedFsContainer container) {
        return registry -> {
            registry.add("storage.endpoint", () -> container.s3Endpoint().toString());
            registry.add(
                    "storage.public-endpoint",
                    () -> container.publicS3Endpoint().toString());
        };
    }

    /** Creates the bucket, as the app does in the {@code local} profile. */
    @Bean
    BucketBootstrap bucketBootstrap(S3Client s3, StorageProperties properties) {
        return new BucketBootstrap(s3, properties);
    }

    /** A named type, so that injecting "the SeaweedFS container" never matches the Postgres container as well. */
    public static class SeaweedFsContainer extends GenericContainer<SeaweedFsContainer> {

        SeaweedFsContainer(String accessKey, String secretKey) {
            super(DockerImageName.parse(SEAWEEDFS_IMAGE));
            withCommand(SEAWEEDFS_COMMAND.toArray(String[]::new));
            withEnv("AWS_ACCESS_KEY_ID", accessKey);
            withEnv("AWS_SECRET_ACCESS_KEY", secretKey);
            withExposedPorts(S3_PORT);
            // Compose's health check: the S3 gateway answers /healthz only once it can reach the filer.
            waitingFor(Wait.forHttp("/healthz").forPort(S3_PORT).forStatusCode(200));
            withStartupTimeout(Duration.ofSeconds(120));
        }

        /** Where the backend reaches the S3 API: the Docker host and the port Docker mapped. */
        public URI s3Endpoint() {
            return URI.create("http://" + getHost() + ":" + getMappedPort(S3_PORT));
        }

        /** The address signed URLs name. A different host from {@link #s3Endpoint()}, on the same port. */
        public URI publicS3Endpoint() {
            return URI.create("http://127.0.0.1:" + getMappedPort(S3_PORT));
        }
    }
}
