package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.memoirist.common.PayloadTooLargeException;
import com.memoirist.common.ValidationFailedException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The allow-list for entry captions (D-05, API_CONTRACT Content rules), as plain logic: no Spring, no Docker. The same
 * rules reach the API in {@code EntryContentTest}.
 */
class EntryContentValidatorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }

    private static JsonNode doc(String... blocks) {
        return json("{\"type\":\"doc\",\"content\":[" + String.join(",", blocks) + "]}");
    }

    private static String paragraph(String... inline) {
        return "{\"type\":\"paragraph\",\"content\":[" + String.join(",", inline) + "]}";
    }

    private static String text(String words) {
        return "{\"type\":\"text\",\"text\":\"" + words + "\"}";
    }

    private static String linkTo(String href) {
        return "{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"link\",\"attrs\":{\"href\":\"" + href
                + "\"}}]}";
    }

    /** What the validator says is wrong with the document: the one message under {@code contentJson}. */
    private static String problemWith(JsonNode doc) {
        return thrownBy(doc).getFieldErrors().get(EntryContentValidator.FIELD);
    }

    private static ValidationFailedException thrownBy(JsonNode doc) {
        Throwable thrown = catchThrowable(() -> EntryContentValidator.validate(doc));
        assertThat(thrown).isInstanceOf(ValidationFailedException.class);
        return (ValidationFailedException) thrown;
    }

    // What is accepted

    @Test
    void anEmptyDocumentAndAnEmptyParagraphAreAccepted() {
        assertThatCode(() -> EntryContentValidator.validate(json("{\"type\":\"doc\"}")))
                .doesNotThrowAnyException();
        assertThatCode(() -> EntryContentValidator.validate(json("{\"type\":\"doc\",\"content\":[]}")))
                .doesNotThrowAnyException();
        assertThatCode(() -> EntryContentValidator.validate(doc("{\"type\":\"paragraph\"}")))
                .doesNotThrowAnyException();
    }

    @Test
    void everyAllowedNodeAndMarkIsAccepted() {
        JsonNode everything = json("""
                {"type":"doc","content":[
                  {"type":"heading","attrs":{"level":1},"content":[{"type":"text","text":"One"}]},
                  {"type":"heading","attrs":{"level":3},"content":[{"type":"text","text":"Three"}]},
                  {"type":"paragraph","content":[
                    {"type":"text","text":"bold","marks":[{"type":"bold"}]},
                    {"type":"text","text":"italic","marks":[{"type":"italic"},{"type":"strike"},{"type":"code"}]},
                    {"type":"hardBreak"},
                    {"type":"text","text":"a link","marks":[{"type":"link","attrs":{"href":"https://example.com","target":"_blank","rel":"noopener","class":null}}]}
                  ]},
                  {"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"a"}]}]}]},
                  {"type":"orderedList","attrs":{"start":1,"type":null},"content":[{"type":"listItem","content":[{"type":"paragraph"}]}]},
                  {"type":"blockquote","content":[{"type":"paragraph","content":[{"type":"text","text":"quoted"}]}]},
                  {"type":"horizontalRule"}
                ]}
                """);

        assertThatCode(() -> EntryContentValidator.validate(everything)).doesNotThrowAnyException();
    }

    @Test
    void nullsForOptionalFieldsCountAsAbsent() {
        JsonNode withNulls = json("""
                {"type":"doc","content":[{"type":"paragraph","attrs":null,"content":[
                  {"type":"text","text":"hi","marks":null}]}]}
                """);

        assertThatCode(() -> EntryContentValidator.validate(withNulls)).doesNotThrowAnyException();
    }

    @Test
    void textMayHoldAnythingBecauseItIsOnlyEverRenderedAsText() {
        // Angle brackets, quotes, emoji and non-Latin letters are words, not markup: the frontend draws text nodes
        // as text (D-05), so they are stored as written.
        JsonNode odd = doc(
                paragraph(text("<script>alert(1)<\\/script> \\\"quoted\\\" \\ud83c\\udf89 caf\\u00e9 \\u65e5\\u672c")));

        assertThatCode(() -> EntryContentValidator.validate(odd)).doesNotThrowAnyException();
    }

    // What is refused: the shape of the document

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "\"doc\"",
                "42",
                "true",
                "{}",
                "{\"type\":\"paragraph\"}",
                "{\"type\":\"Doc\"}",
                "{\"type\":null}",
                "{\"type\":7}",
                "{\"type\":[\"doc\"]}",
            })
    void aRootThatIsNotADocIsRefused(String text) {
        assertThat(problemWith(json(text))).startsWith("must be a TipTap document");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "image",
                "codeBlock",
                "table",
                "script",
                "html",
                "iframe",
                "mention",
                "taskList",
                "Paragraph",
                "paragraph ",
                "",
            })
    void aNodeTypeThatIsNotOnTheListIsRefusedAndNotRepeated(String type) {
        JsonNode withUnknown = doc(paragraph(text("fine")), "{\"type\":\"" + type + "\"}");

        String problem = problemWith(withUnknown);

        assertThat(problem).isEqualTo("has a node type that is not allowed (at content[1])");
        if (!type.isEmpty()) {
            assertThat(problem).as("the client's own text is never echoed").doesNotContain(type.trim());
        }
    }

    @Test
    void aDocNestedInsideADocumentIsRefused() {
        JsonNode nested = doc("{\"type\":\"doc\",\"content\":[]}");

        assertThat(problemWith(nested)).isEqualTo("has a node type that is not allowed (at content[0])");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"text\"", "7", "null", "[]", "true"})
    void aChildThatIsNotAnObjectIsRefused(String child) {
        assertThat(problemWith(doc(child))).isEqualTo("has a node that is not an object (at content[0])");
    }

    @Test
    void theNodeTypeMustBeAString() {
        assertThat(problemWith(doc("{\"type\":5}"))).startsWith("has a node type that is not allowed");
        assertThat(problemWith(doc("{\"content\":[]}"))).startsWith("has a node type that is not allowed");
    }

    @Test
    void aNodeMayNotCarryFieldsOutsideTheFiveTipTapUses() {
        // The classic place to hide HTML: a field that no renderer reads today and a later one might.
        JsonNode withHtml = doc("{\"type\":\"paragraph\",\"html\":\"<img src=x onerror=alert(1)>\"}");

        assertThat(problemWith(withHtml)).isEqualTo("has a node field that is not allowed (at content[0])");
        assertThat(problemWith(json("{\"type\":\"doc\",\"style\":\"x\"}")))
                .isEqualTo("has a node field that is not allowed (at the document)");
    }

    @Test
    void aTextNodeNeedsItsTextAndOnlyATextNodeMayHaveSome() {
        assertThat(problemWith(doc(paragraph("{\"type\":\"text\"}"))))
                .isEqualTo("has a text node without text (at content[0].content[0])");
        assertThat(problemWith(doc(paragraph("{\"type\":\"text\",\"text\":42}"))))
                .isEqualTo("has a text node without text (at content[0].content[0])");
        assertThat(problemWith(doc("{\"type\":\"paragraph\",\"text\":\"loose words\"}")))
                .isEqualTo("has text on a node that is not a text node (at content[0])");
    }

    @Test
    void leavesCannotHaveContentAndContentMustBeAList() {
        assertThat(problemWith(doc(paragraph("{\"type\":\"text\",\"text\":\"a\",\"content\":[]}"))))
                .isEqualTo("has content where it is not allowed (at content[0].content[0])");
        assertThat(problemWith(doc(paragraph("{\"type\":\"hardBreak\",\"content\":[]}"))))
                .isEqualTo("has content where it is not allowed (at content[0].content[0])");
        assertThat(problemWith(doc("{\"type\":\"horizontalRule\",\"content\":[]}")))
                .isEqualTo("has content where it is not allowed (at content[0])");
        assertThat(problemWith(doc("{\"type\":\"paragraph\",\"content\":{\"type\":\"text\"}}")))
                .isEqualTo("has content where it is not allowed (at content[0])");
    }

    @Test
    void anErrorInsideALongDocumentSaysWhere() {
        JsonNode deep = doc(
                paragraph(text("fine")),
                "{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"content\":[{\"type\":\"image\"}]}]}");

        assertThat(problemWith(deep))
                .isEqualTo("has a node type that is not allowed (at content[1].content[0].content[0])");
    }

    @Test
    void attrsMustBeAnObject() {
        assertThat(problemWith(doc("{\"type\":\"paragraph\",\"attrs\":\"x\"}")))
                .isEqualTo("has node attributes that are not an object (at content[0])");
    }

    // Headings

    @ParameterizedTest
    @ValueSource(strings = {"0", "4", "-1", "7", "\"2\"", "1.5", "null", "true", "99999999999"})
    void aHeadingLevelOutsideOneToThreeIsRefused(String level) {
        JsonNode heading = doc("{\"type\":\"heading\",\"attrs\":{\"level\":" + level + "},\"content\":[]}");

        assertThat(problemWith(heading)).isEqualTo("has a heading level that is not 1, 2 or 3 (at content[0])");
    }

    @Test
    void aHeadingWithoutALevelIsRefused() {
        assertThat(problemWith(doc("{\"type\":\"heading\"}"))).contains("heading level");
        assertThat(problemWith(doc("{\"type\":\"heading\",\"attrs\":{}}"))).contains("heading level");
    }

    // Marks

    @ParameterizedTest
    @ValueSource(strings = {"underline", "textStyle", "highlight", "subscript", "Bold", "script", ""})
    void aMarkThatIsNotOnTheListIsRefusedAndNotRepeated(String mark) {
        JsonNode withMark =
                doc(paragraph("{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"" + mark + "\"}]}"));

        String problem = problemWith(withMark);

        assertThat(problem).isEqualTo("has a mark that is not allowed (at content[0].content[0].marks[0])");
        if (!mark.isEmpty()) {
            assertThat(problem).doesNotContain(mark);
        }
    }

    @Test
    void marksMustBeAListOfObjectsWithOnlyATypeAndAttrs() {
        assertThat(problemWith(doc(paragraph("{\"type\":\"text\",\"text\":\"x\",\"marks\":{\"type\":\"bold\"}}"))))
                .isEqualTo("has marks that are not a list (at content[0].content[0].marks)");
        assertThat(problemWith(doc(paragraph("{\"type\":\"text\",\"text\":\"x\",\"marks\":[\"bold\"]}"))))
                .isEqualTo("has a mark that is not an object (at content[0].content[0].marks[0])");
        assertThat(problemWith(
                        doc(paragraph("{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"bold\",\"x\":1}]}"))))
                .isEqualTo("has a mark field that is not allowed (at content[0].content[0].marks[0])");
        assertThat(problemWith(doc(
                        paragraph("{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"bold\",\"attrs\":5}]}"))))
                .isEqualTo("has mark attributes that are not an object (at content[0].content[0].marks[0])");
    }

    // Links

    @ParameterizedTest
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "JavaScript:alert(1)",
                "JAVASCRIPT:alert(1)",
                " javascript:alert(1)",
                "\\tjavascript:alert(1)",
                "java\\tscript:alert(1)",
                "java\\nscript:alert(1)",
                "java\\rscript:alert(1)",
                "\\u0000javascript:alert(1)",
                "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
                "vbscript:msgbox(1)",
                "file:///etc/passwd",
                "ftp://example.com",
                "//evil.example/path",
                "/relative/path",
                "example.com",
                "www.example.com",
                "https:example.com",
                "http:/example.com",
                "https://",
                "mailto:",
                "",
                "   ",
            })
    void aLinkThatIsNotHttpHttpsOrMailtoIsRefused(String href) {
        JsonNode withLink = doc(paragraph(linkTo(href)));

        assertThat(problemWith(withLink))
                .isEqualTo(
                        "has a link that is not an http, https or mailto address (at content[0].content[0].marks[0])");
    }

    @Test
    void aLinkWithoutAnAddressOrWithAnAddressThatIsNotTextIsRefused() {
        String problem = "has a link that is not an http, https or mailto address (at content[0].content[0].marks[0])";
        assertThat(problemWith(doc(paragraph("{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"link\"}]}"))))
                .isEqualTo(problem);
        assertThat(problemWith(doc(
                        paragraph("{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"link\",\"attrs\":{}}]}"))))
                .isEqualTo(problem);
        assertThat(
                        problemWith(
                                doc(
                                        paragraph(
                                                "{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"link\",\"attrs\":{\"href\":null}}]}"))))
                .isEqualTo(problem);
        assertThat(
                        problemWith(
                                doc(
                                        paragraph(
                                                "{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"link\",\"attrs\":{\"href\":[\"https://a.example\"]}}]}"))))
                .isEqualTo(problem);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://example.com",
                "https://example.com/a/b?c=d&e=f#g",
                "http://example.com",
                "HTTPS://EXAMPLE.COM",
                "Http://Example.com/Path",
                "mailto:me@example.com",
                "MAILTO:me@example.com",
                "mailto:me@example.com?subject=Hi%20there",
                "  https://example.com  ",
                "https://example.com/caf\\u00e9",
                "https://\\u4f8b\\u3048.jp/",
                "https://localhost:8080/x",
                "https://user@example.com:8443/p",
                "https://example.com/a b",
            })
    void anHttpHttpsOrMailtoLinkIsAccepted(String href) {
        JsonNode withLink = doc(paragraph(linkTo(href)));

        assertThatCode(() -> EntryContentValidator.validate(withLink)).doesNotThrowAnyException();
    }

    @Test
    void isAllowedLinkAnswersWithoutNeedingADocument() {
        assertThat(EntryContentValidator.isAllowedLink("https://example.com")).isTrue();
        assertThat(EntryContentValidator.isAllowedLink("java\tscript:alert(1)")).isFalse();
        assertThat(EntryContentValidator.isAllowedLink("\u0001https://example.com"))
                .isFalse();
        assertThat(EntryContentValidator.isAllowedLink("https://example.com/\u007f"))
                .isFalse();
        assertThat(EntryContentValidator.isAllowedLink("https://example.com/\u0085"))
                .isFalse();
    }

    // Size

    /** A document whose compact JSON is exactly {@code bytes} long, made of {@code filler} repeated in one text node. */
    private static JsonNode documentOf(int bytes, String filler) {
        String frame = JSON.writeValueAsString(doc(paragraph(text(""))));
        int fillerBytes = filler.getBytes(StandardCharsets.UTF_8).length;
        int room = bytes - frame.getBytes(StandardCharsets.UTF_8).length;
        assertThat(room % fillerBytes).as("the filler must fit exactly").isZero();
        JsonNode result = doc(paragraph(text(filler.repeat(room / fillerBytes))));
        assertThat(JSON.writeValueAsString(result).getBytes(StandardCharsets.UTF_8))
                .hasSize(bytes);
        return result;
    }

    @Test
    void aDocumentOfExactly200KbIsAcceptedAndOneByteMoreIsTooLarge() {
        int limit = 200 * 1024;
        assertThat(EntryContentValidator.MAX_BYTES).isEqualTo(limit);

        assertThatCode(() -> EntryContentValidator.validate(documentOf(limit, "a")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> EntryContentValidator.validate(documentOf(limit + 1, "a")))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessage("The content is larger than 200 KB.");
    }

    @Test
    void sizeIsCountedInBytesNotCharacters() {
        // "é" is 2 bytes in UTF-8, so a document three bytes over the limit has about 102,000 characters: far fewer
        // characters than the limit, and still too large.
        JsonNode accents = documentOf(200 * 1024 + 3, "é");
        assertThat(accents.get("content")
                        .get(0)
                        .get("content")
                        .get(0)
                        .get("text")
                        .stringValue()
                        .length())
                .as("fewer characters than bytes")
                .isLessThan(EntryContentValidator.MAX_BYTES);

        assertThatThrownBy(() -> EntryContentValidator.validate(accents)).isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void anOversizedDocumentIsRefusedBeforeItsShapeIsLookedAt() {
        // Too big and also wrong: the size answer comes first, so a huge body is not walked node by node.
        String huge = "x".repeat(EntryContentValidator.MAX_BYTES);
        JsonNode bigAndWrong = doc("{\"type\":\"image\",\"junk\":\"" + huge + "\"}");

        assertThatThrownBy(() -> EntryContentValidator.validate(bigAndWrong))
                .isInstanceOf(PayloadTooLargeException.class);
    }
}
