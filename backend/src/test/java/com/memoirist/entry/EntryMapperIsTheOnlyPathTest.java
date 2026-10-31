package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Non-negotiable 4 as a test: {@code EntryMapper} is the only place an {@code Entry} becomes a response. A second place
 * that built an {@code EntryResponse} would be a second place that could forget to hide a sealed Loot Drop.
 *
 * <p>Java can't make a record's constructor private to one class, so this reads the source files instead. It is
 * deliberately blunt (it looks for the text {@code new EntryResponse(}), and a plain check that cannot be reasoned
 * around is what a rule like this needs. The list rows ({@code EntrySummaryResponse}, task 3.9) join the list below
 * when they exist.
 */
class EntryMapperIsTheOnlyPathTest {

    /** Gradle runs the tests with the project folder ({@code backend/}) as the working directory. */
    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    private static final List<String> DTOS_ONLY_THE_MAPPER_BUILDS = List.of("new EntryResponse(");

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void theScanReallyReadsTheSources() throws IOException {
        assertThat(javaFiles())
                .as("a wrong working directory would make the next test pass for nothing")
                .hasSizeGreaterThan(50);
    }

    @Test
    void onlyEntryMapperBuildsAnEntryResponse() throws IOException {
        for (String construction : DTOS_ONLY_THE_MAPPER_BUILDS) {
            List<String> builders = javaFiles().stream()
                    .filter(path -> contains(path, construction))
                    .map(path -> path.getFileName().toString())
                    .toList();

            assertThat(builders).as("the files that contain `%s`", construction).containsExactly("EntryMapper.java");
        }
    }

    @Test
    void noOtherClassNamesTheResponseRecordsBesideTheMapperAndTheirOwnDocs() throws IOException {
        // A call to a *method reference* to the constructor would slip past the text above, so look for that too.
        List<String> references = javaFiles().stream()
                .filter(path -> contains(path, "EntryResponse::new"))
                .map(path -> path.getFileName().toString())
                .toList();

        assertThat(references).isEmpty();
    }

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
