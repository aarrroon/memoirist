// memoirist backend: Spring Boot 4 on Java 25 (D-02), built with the Gradle Kotlin DSL and formatted by Spotless (D-15).
// Versions are pinned here. A dependency without a version takes the one Spring Boot's BOM manages.
plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.4"
}

group = "com.memoirist"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        // Compile and run the tests on JDK 25, whichever JDK started Gradle.
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        // Spring Boot doesn't manage the AWS SDK. Its BOM keeps every SDK module on one version.
        mavenBom("software.amazon.awssdk:bom:2.57.6")
    }
}

// Mockito's jar, on its own, to load as a Java agent when the test JVM starts (see the Test task below).
val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    // Spring Boot 4 has a starter for each technology: "web" is now spring-boot-starter-webmvc, and
    // Flyway needs spring-boot-starter-flyway, because flyway-core alone no longer turns on its auto-configuration.
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // springdoc-openapi 3.x is the line for Spring Boot 4; 3.2.0 is built against Boot 4.1.2.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")
    implementation("software.amazon.awssdk:s3")

    // Each test starter brings spring-boot-starter-test (JUnit, AssertJ, Mockito) plus the test support
    // for its technology, such as @WebMvcTest, @DataJpaTest and RestTestClient.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

// The API contract, which ProblemKindTest compares the error types with. Gradle passes its absolute path to the tests,
// so they don't depend on the folder they start in, and lists it as an input, so editing the contract re-runs them.
val apiContract = rootProject.projectDir.resolve("../docs/API_CONTRACT.md").canonicalFile

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("memoirist.apiContract", apiContract.path)
    if (apiContract.exists()) {
        inputs.file(apiContract).withPropertyName("apiContract")
    }
    // The app runs in UTC (MemoiristApplication.main), so the tests do too, on every machine and from the first test,
    // whatever the host's zone. MemoiristApplicationTest checks main() itself, starting from another zone.
    systemProperty("user.timezone", "UTC")
    // Since Java 21 the JDK warns when a library attaches an agent to its own JVM, as Mockito does for
    // inline mocks, and a future release will refuse it (JEP 451). Loading Mockito at startup avoids both.
    // The provider resolves the jar when the tests run, not while Gradle reads this file.
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${mockitoAgent.singleFile}") })
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

spotless {
    java {
        // 4-space indents and 120 columns. The version is pinned so that upgrading Spotless never reformats the code.
        palantirJavaFormat("2.102.0")
    }
}
