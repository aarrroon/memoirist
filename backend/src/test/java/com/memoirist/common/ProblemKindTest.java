package com.memoirist.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Keeps {@link ProblemKind} and the table in {@code docs/API_CONTRACT.md} (Errors) saying the same thing. The contract
 * is written first and the code follows it, so a type added to one and not the other is a bug in whichever is behind.
 *
 * <p>Gradle passes the contract's path in the {@code memoirist.apiContract} system property (build.gradle.kts). Without
 * it (a test run straight from an IDE) the path falls back to {@code ../docs/API_CONTRACT.md}, which is right when
 * the working directory is {@code backend}. A backend built on its own, without the {@code docs} folder beside it,
 * fails with a message saying so instead of a bare {@code NoSuchFileException}.
 */
class ProblemKindTest {

    private static final Path CONTRACT =
            Path.of(System.getProperty("memoirist.apiContract", "../docs/API_CONTRACT.md"));

    /** A table row such as {@code | `not-found` | 404 | Missing ... |}: the type suffix and the status. */
    private static final Pattern ROW = Pattern.compile("^\\| `([a-z-]+)` \\| (\\d{3}) \\|");

    @Test
    void theContractTableAndTheCodeListTheSameTypesWithTheSameStatuses() throws IOException {
        assertThat(CONTRACT)
                .as("docs/API_CONTRACT.md, which this test compares ProblemKind with. It lives outside backend/, so the"
                        + " backend can't be tested on its own without the docs folder beside it")
                .isRegularFile();

        Map<String, Integer> inContract = new TreeMap<>();
        boolean inErrorsSection = false;
        for (String line : Files.readAllLines(CONTRACT)) {
            if (line.startsWith("### ")) {
                inErrorsSection = line.startsWith("### Errors");
            }
            Matcher row = ROW.matcher(line);
            if (inErrorsSection && row.find()) {
                inContract.put(row.group(1), Integer.valueOf(row.group(2)));
            }
        }

        Map<String, Integer> inCode = new TreeMap<>();
        for (ProblemKind kind : ProblemKind.values()) {
            inCode.put(kind.typeSuffix(), kind.status().value());
        }

        assertThat(inContract)
                .as("the type rows found in API_CONTRACT.md (Errors); an empty map means the table moved")
                .isNotEmpty();
        assertThat(inCode).isEqualTo(inContract);
    }

    @Test
    void everyKindHasItsOwnSuffixAndATitleAndASafeDefaultDetail() {
        assertThat(Arrays.stream(ProblemKind.values()).map(ProblemKind::typeSuffix))
                .doesNotHaveDuplicates();
        for (ProblemKind kind : ProblemKind.values()) {
            assertThat(kind.title()).as("title of %s", kind).isNotBlank();
            // The default detail is what a client sees when nothing more specific is known. It must never be code.
            assertThat(kind.defaultDetail())
                    .as("default detail of %s", kind)
                    .isNotBlank()
                    .doesNotContain("Exception")
                    .doesNotContain("com.memoirist");
        }
    }
}
