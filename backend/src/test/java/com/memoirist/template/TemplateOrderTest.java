package com.memoirist.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * {@link Template#BY_NAME}, the order of {@code GET /templates}. Plain Java, no database: the point of sorting in Java
 * is that the answer doesn't depend on how Postgres was set up.
 */
class TemplateOrderTest {

    private static final UUID USER = UUID.randomUUID();

    private static Template named(String name) {
        return new Template(UUID.randomUUID(), USER, name, List.of());
    }

    private static Template withId(String id, String name) {
        return new Template(UUID.fromString(id), USER, name, List.of());
    }

    private static List<String> namesInOrder(Template... templates) {
        return Stream.of(templates)
                .sorted(Template.BY_NAME)
                .map(Template::getName)
                .toList();
    }

    @Test
    void capitalsAndSmallLettersAreSortedTogether() {
        // Plain String order would put "Banana" and "Zebra" before "apple" (every capital sorts before every small
        // letter), and a database with the C collation would do the same.
        assertThat(namesInOrder(named("Zebra"), named("apple"), named("Banana")))
                .containsExactly("apple", "Banana", "Zebra");
    }

    @Test
    void anAccentedLetterSortsNextToItsBaseLetterNotAfterZ() {
        // Comparing characters one by one would put "Élan" after "Zebra" (U+00C9 is above "z").
        assertThat(namesInOrder(named("Zebra"), named("Élan"), named("Eagle"), named("Fox")))
                .containsExactly("Eagle", "Élan", "Fox", "Zebra");
    }

    @Test
    void namesThatDifferOnlyInCaseKeepAFixedOrderOfTheirOwn() {
        // Equal as letters, so the exact spelling decides (the capital first), whatever order they arrive in.
        assertThat(namesInOrder(named("trip day"), named("Trip day"))).containsExactly("Trip day", "trip day");
        assertThat(namesInOrder(named("Trip day"), named("trip day"))).containsExactly("Trip day", "trip day");
    }

    @Test
    void aShorterNameComesBeforeALongerOneThatStartsWithIt() {
        assertThat(namesInOrder(named("My week 2"), named("My week"), named("My")))
                .containsExactly("My", "My week", "My week 2");
    }

    @Test
    void digitsSortAsTextSoTenComesBeforeTwo() {
        // Documented, not clever: "by name" is alphabetical, not numeric ("natural") order.
        assertThat(namesInOrder(named("Night out"), named("2 days"), named("10 days")))
                .containsExactly("10 days", "2 days", "Night out");
    }

    @Test
    void twoTemplatesWithTheSameNameAreOrderedByIdSoTheListIsAlwaysTheSame() {
        Template first = withId("00000000-0000-0000-0000-00000000000a", "Trip day");
        Template second = withId("00000000-0000-0000-0000-00000000000b", "Trip day");

        assertThat(Stream.of(second, first).sorted(Template.BY_NAME).toList()).containsExactly(first, second);
        assertThat(Stream.of(first, second).sorted(Template.BY_NAME).toList()).containsExactly(first, second);
    }
}
