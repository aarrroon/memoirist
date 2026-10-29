package com.memoirist;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Starts the whole app on a random port, against Postgres in Docker, and calls it over real HTTP: the
 * health endpoint, the OpenAPI document and Swagger UI (task 2.1's Done-when list).
 *
 * <p>{@code show-components} makes the health response name each check (database, disk space and so on)
 * without showing their details. It's set for this test only; the app's own settings belong to task 2.2.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "management.endpoint.health.show-components=always")
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ApplicationSmokeTest {

    @Autowired
    RestTestClient client;

    @Test
    void healthIsUpAndIncludesTheDatabase() {
        // TODO(human) PRACTICE: Prove that the app reports UP *and* that Postgres is one of the checks behind that
        //   UP. Write the request and its assertions with RestTestClient.
        // Hint: by default /actuator/health is just {"groups":[...],"status":"UP"}; /actuator/health/db is a 404.
        //   This class turns show-components on. Which status code and JSON paths prove the database was checked?
        // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
        client.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP")
                .jsonPath("$.components.db.status")
                .isEqualTo("UP");
        // └── END ANSWER ▲ ──
    }

    @Test
    void servesTheOpenApiDocument() {
        client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.openapi")
                .value(String.class, version -> assertThat(version).startsWith("3."));
    }

    @Test
    void swaggerUiLoads() {
        // springdoc redirects /swagger-ui.html to /swagger-ui/index.html, and the client follows redirects.
        client.get()
                .uri("/swagger-ui.html")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("<title>Swagger UI</title>"));
    }
}
