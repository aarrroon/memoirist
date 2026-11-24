package com.memoirist.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.SeaweedFsTestConfiguration;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.FileSystemResource;

/**
 * Keeps the SeaweedFS that the tests start the same as the one Docker Compose starts (D-03, Version: one pinned tag,
 * used by both). A test on a different release than the one a developer runs would pass for the wrong reason.
 *
 * <p>Gradle passes the path of {@code docker-compose.yml} in the {@code memoirist.composeFile} system property
 * (build.gradle.kts). Without it (a run straight from an IDE) the path falls back to {@code ../docker-compose.yml},
 * which is right when the working directory is {@code backend}.
 */
class SeaweedFsComposeTest {

    private static final Path COMPOSE = Path.of(System.getProperty("memoirist.composeFile", "../docker-compose.yml"));

    @SuppressWarnings("unchecked")
    private Map<String, Object> composeService(String name) {
        assertThat(COMPOSE)
                .as("docker-compose.yml, which this test compares the Testcontainers SeaweedFS with. It lives outside"
                        + " backend/, so the backend can't be tested on its own without the repository around it")
                .isRegularFile();
        YamlMapFactoryBean yaml = new YamlMapFactoryBean();
        yaml.setResources(new FileSystemResource(COMPOSE));
        Map<String, Object> compose = yaml.getObject();
        assertThat(compose).containsKey("services");
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        assertThat(services).containsKey(name);
        return (Map<String, Object>) services.get(name);
    }

    @Test
    void theTestContainerRunsTheImageAndTheCommandThatComposeRuns() {
        Map<String, Object> seaweedfs = composeService("seaweedfs");

        assertThat(seaweedfs.get("image")).isEqualTo(SeaweedFsTestConfiguration.SEAWEEDFS_IMAGE);
        assertThat(seaweedfs.get("command")).isEqualTo(SeaweedFsTestConfiguration.SEAWEEDFS_COMMAND);
    }

    @Test
    void composeGivesTheS3KeysAsEnvironmentVariablesLikeTheTestContainerDoes() {
        @SuppressWarnings("unchecked")
        Map<String, Object> environment =
                (Map<String, Object>) composeService("seaweedfs").get("environment");

        // The names SeaweedFS reads (D-03, Local credentials). SeaweedFsTestConfiguration sets these two.
        assertThat(environment).containsOnlyKeys("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY");
    }
}
