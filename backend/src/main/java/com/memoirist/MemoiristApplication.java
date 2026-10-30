package com.memoirist;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Starts memoirist.
 *
 * <p>{@code @ConfigurationPropertiesScan} registers every {@code @ConfigurationProperties} record in this package and
 * below, such as {@link com.memoirist.storage.StorageProperties}, so each one is bound and validated at startup.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MemoiristApplication {

    public static void main(String[] args) {
        // UTC before Spring starts, so log timestamps, JDBC sessions and anything else that reads the JVM's zone agree,
        // whatever the host's zone is. "Today" and week boundaries still use each user's own zone (D-07).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(MemoiristApplication.class, args);
    }
}
