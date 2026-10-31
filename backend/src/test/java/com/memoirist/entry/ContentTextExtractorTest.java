package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plain text and the preview line from a TipTap document (D-05, D-24), as plain logic. The rule for the preview line:
 * the first line that isn't a heading, or the first heading if there is nothing else, at most 120 characters.
 */
class ContentTextExtractorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode doc(String... blocks) {
        return JSON.readTree("{\"type\":\"doc\",\"content\":[" + String.join(",", blocks) + "]}");
    }

    private static String p(String words) {
        return "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"" + words + "\"}]}";
    }

    private static String h(int level, String words) {
        return "{\"type\":\"heading\",\"attrs\":{\"level\":" + level + "},\"content\":[{\"type\":\"text\",\"text\":\""
                + words + "\"}]}";
    }

    @Test
    void noCaptionIsNoTextAndNoPreview() {
        ContentTextExtractor.Extracted none = ContentTextExtractor.extract(null);

        assertThat(none.text()).isEmpty();
        assertThat(none.previewLine()).isNull();
    }

    @Test
    void anEmptyDocumentAndEmptyParagraphsAreNoTextAndNoPreview() {
        assertThat(ContentTextExtractor.extract(JSON.readTree("{\"type\":\"doc\"}"))
                        .text())
                .isEmpty();
        ContentTextExtractor.Extracted blank = ContentTextExtractor.extract(
                doc("{\"type\":\"paragraph\"}", p("   "), "{\"type\":\"horizontalRule\"}"));

        assertThat(blank.text()).isEmpty();
        assertThat(blank.previewLine()).isNull();
    }

    @Test
    void eachParagraphIsALineAndMarksDisappear() {
        ContentTextExtractor.Extracted extracted = ContentTextExtractor.extract(doc(
                p("Just arrived at the campsite."),
                "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Cold, \"},"
                        + "{\"type\":\"text\",\"text\":\"very\",\"marks\":[{\"type\":\"bold\"}]},"
                        + "{\"type\":\"text\",\"text\":\" cold.\"}]}"));

        assertThat(extracted.text()).isEqualTo("Just arrived at the campsite.\nCold, very cold.");
        assertThat(extracted.previewLine()).isEqualTo("Just arrived at the campsite.");
    }

    @Test
    void aHardBreakStartsANewLine() {
        JsonNode withBreak = doc(
                "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"one\"},{\"type\":\"hardBreak\"},"
                        + "{\"type\":\"text\",\"text\":\"two\"}]}");

        assertThat(ContentTextExtractor.extract(withBreak).text()).isEqualTo("one\ntwo");
    }

    @Test
    void aLineBreakInsideATextNodeAlsoStartsANewLine() {
        // ProseMirror never writes one, but a client might, and a preview line must never hold a line break.
        ContentTextExtractor.Extracted extracted = ContentTextExtractor.extract(doc(p("first\\nsecond\\r\\nthird")));

        assertThat(extracted.text()).isEqualTo("first\nsecond\nthird");
        assertThat(extracted.previewLine()).isEqualTo("first");
    }

    @Test
    void listItemsAndQuotesAreLinesToo() {
        JsonNode lists = doc(
                "{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"content\":[" + p("milk") + "]},"
                        + "{\"type\":\"listItem\",\"content\":[" + p("bread") + "]}]}",
                "{\"type\":\"blockquote\",\"content\":[" + p("a quote") + "]}",
                "{\"type\":\"orderedList\",\"content\":[{\"type\":\"listItem\",\"content\":[" + p("first") + "]}]}");

        assertThat(ContentTextExtractor.extract(lists).text()).isEqualTo("milk\nbread\na quote\nfirst");
    }

    @Test
    void anEmptyParagraphBetweenTwoKeepsTheGap() {
        JsonNode spaced = doc(p("before"), "{\"type\":\"paragraph\"}", p("after"));

        assertThat(ContentTextExtractor.extract(spaced).text()).isEqualTo("before\n\nafter");
    }

    @Test
    void leadingAndTrailingBlankLinesAreDropped() {
        JsonNode padded = doc("{\"type\":\"paragraph\"}", p("middle"), "{\"type\":\"paragraph\"}");

        assertThat(ContentTextExtractor.extract(padded).text()).isEqualTo("middle");
    }

    // The preview line (D-24)

    @Test
    void thePreviewSkipsHeadingsSoATemplateEntryIsNotNamedAfterItsFirstPrompt() {
        JsonNode fromTemplate = doc(
                h(2, "What triggered the spiral?"), p("A message I left on read."), h(2, "What helped?"), p("A walk."));

        ContentTextExtractor.Extracted extracted = ContentTextExtractor.extract(fromTemplate);

        assertThat(extracted.previewLine()).isEqualTo("A message I left on read.");
        assertThat(extracted.text())
                .isEqualTo("What triggered the spiral?\nA message I left on read.\nWhat helped?\nA walk.");
    }

    @Test
    void ifThereAreOnlyHeadingsThePreviewIsTheFirstHeading() {
        JsonNode onlyHeadings = doc(h(1, "Night out"), h(2, "Who was there?"));

        assertThat(ContentTextExtractor.extract(onlyHeadings).previewLine()).isEqualTo("Night out");
    }

    @Test
    void emptyLinesAreNotThePreview() {
        JsonNode gaps = doc("{\"type\":\"paragraph\"}", p("  "), h(1, "A title"), p("   the real first line  "));

        assertThat(ContentTextExtractor.extract(gaps).previewLine()).isEqualTo("the real first line");
    }

    @Test
    void theSecondHalfOfAHeadingAfterABreakIsStillAHeading() {
        JsonNode brokenHeading = doc(
                "{\"type\":\"heading\",\"attrs\":{\"level\":2},\"content\":[{\"type\":\"text\",\"text\":\"Part one\"},"
                        + "{\"type\":\"hardBreak\"},{\"type\":\"text\",\"text\":\"part two\"}]}",
                p("Body text"));

        assertThat(ContentTextExtractor.extract(brokenHeading).previewLine()).isEqualTo("Body text");
    }

    @Test
    void thePreviewIsAtMost120CharactersAndTheTextIsNotCut() {
        String longLine = "word ".repeat(60).strip(); // 299 characters

        ContentTextExtractor.Extracted extracted = ContentTextExtractor.extract(doc(p(longLine)));

        assertThat(extracted.previewLine()).hasSize(119); // 120 cut, then the trailing space stripped
        assertThat(longLine).startsWith(extracted.previewLine());
        assertThat(extracted.text()).isEqualTo(longLine);
    }

    @Test
    void aLineOfExactly120CharactersIsKeptWhole() {
        String exact = "x".repeat(120);

        assertThat(ContentTextExtractor.extract(doc(p(exact))).previewLine()).isEqualTo(exact);
        assertThat(ContentTextExtractor.extract(doc(p(exact + "y"))).previewLine())
                .isEqualTo(exact);
    }

    @Test
    void anEmojiIsNeverCutInHalf() {
        // Each party popper is two Java chars. 119 letters + 1 emoji = 120 code points (121 chars); one more emoji
        // would be 121 code points and must be cut off whole.
        String line = "a".repeat(119) + "\\ud83c\\udf89\\ud83c\\udf89";

        String preview = ContentTextExtractor.extract(doc(p(line))).previewLine();

        assertThat(preview).isEqualTo("a".repeat(119) + "🎉");
        assertThat(preview.codePointCount(0, preview.length())).isEqualTo(120);
        assertThat(Character.isHighSurrogate(preview.charAt(preview.length() - 2)))
                .isTrue();
        assertThat(Character.isLowSurrogate(preview.charAt(preview.length() - 1)))
                .isTrue();
    }

    // Documents the validator would refuse still can't break the extractor

    @Test
    void oddShapesAddNothingInsteadOfFailing() {
        JsonNode odd = JSON.readTree("""
                {"type":"doc","content":[
                  "loose string", 7, null, [],
                  {"content":[{"type":"text","text":"no type, but a text child"}]},
                  {"type":"paragraph","content":"not a list"},
                  {"type":"paragraph","content":[{"type":"text","text":5},{"type":"text"}]},
                  {"type":"paragraph","content":[{"type":"text","text":"survivor"}]}
                ]}
                """);

        assertThat(ContentTextExtractor.extract(odd).text()).contains("survivor");
    }

    @Test
    void aDocumentThatIsNotAnObjectGivesNothing() {
        assertThat(ContentTextExtractor.extract(JSON.readTree("[1,2]")).text()).isEmpty();
        assertThat(ContentTextExtractor.extract(JSON.readTree("\"text\"")).previewLine())
                .isNull();
    }
}
