package com.memoirist;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Starts the whole app on a random port, against Postgres in Docker, and calls it over real HTTP: the
 * health endpoint, the OpenAPI document and Swagger UI (task 2.1's Done-when list).
 *
 * <p>The {@code test} profile turns on {@code show-components} (application-test.yml), which makes the health
 * response name each check (database, disk space and so on) without showing their details.
 */
@IntegrationTest
class ApplicationSmokeTest {

    @Autowired
    RestTestClient client;

    @Test
    void healthIsUpAndIncludesTheDatabase() {
        // TODO(human) PRACTICE: Prove that the app reports UP *and* that Postgres is one of the checks behind that
        //   UP. Write the request and its assertions with RestTestClient.
        // Hint: by default /actuator/health is just {"groups":[...],"status":"UP"}; /actuator/health/db is a 404.
        //   The test profile turns show-components on. Which status code and JSON paths prove the database was checked?
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
