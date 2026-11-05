package com.memoirist.tag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * {@link Tag#BY_NAME}, the order of {@code GET /tags} and of the {@code tags} on an entry. Plain Java, no database: the
 * point of sorting in Java is that the answer doesn't depend on how Postgres was set up. (The same collation is shared
 * with templates through {@code NameCollation}; {@code TemplateOrderTest} covers more of its letters.)
 */
class TagOrderTest {

    private static final UUID USER = UUID.randomUUID();

    private static Tag named(String name) {
        return new Tag(UUID.randomUUID(), USER, name);
    }

    private static List<String> namesInOrder(Tag... tags) {
        return Stream.of(tags).sorted(Tag.BY_NAME).map(Tag::getName).toList();
    }

    @Test
    void capitalsAndSmallLettersAreSortedTogether() {
        // Plain String order would put "Family" and "Work" before "going out".
        assertThat(namesInOrder(named("Work"), named("going out"), named("Family")))
                .containsExactly("Family", "going out", "Work");
    }

    @Test
    void anAccentedLetterSortsNextToItsBaseLetter() {
        assertThat(namesInOrder(named("Zoo"), named("Éclair"), named("Eagle"), named("Fox")))
                .containsExactly("Eagle", "Éclair", "Fox", "Zoo");
    }

    @Test
    void digitsSortAsTextSoTenComesBeforeTwo() {
        assertThat(namesInOrder(named("Night"), named("2 days"), named("10 days")))
                .containsExactly("10 days", "2 days", "Night");
    }

    @Test
    void namesThatLookTheSameKeepAFixedOrderWhateverOrderTheyArriveIn() {
        // The database refuses names that differ only in case, but "Cafe" and "Café" can both exist. They are equal as
        // letters, so the exact spelling decides.
        assertThat(namesInOrder(named("Café"), named("Cafe"))).containsExactly("Cafe", "Café");
        assertThat(namesInOrder(named("Cafe"), named("Café"))).containsExactly("Cafe", "Café");
    }

    @Test
    void twoTagsWithTheSameNameAreOrderedByIdSoTheListIsAlwaysTheSame() {
        Tag first = new Tag(UUID.fromString("00000000-0000-0000-0000-00000000000a"), USER, "Work");
        Tag second = new Tag(UUID.fromString("00000000-0000-0000-0000-00000000000b"), USER, "Work");

        assertThat(Stream.of(second, first).sorted(Tag.BY_NAME).toList()).containsExactly(first, second);
        assertThat(Stream.of(first, second).sorted(Tag.BY_NAME).toList()).containsExactly(first, second);
    }

    @Test
    void anUnsavedTagWithNoCreatedAtDoesNotBreakTheSort() {
        // createdAt is filled in by Hibernate when a row is written, so a tag built in memory has none.
        assertThat(namesInOrder(named("b"), named("a"))).containsExactly("a", "b");
    }
}
