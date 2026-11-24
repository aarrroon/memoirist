package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Captions through the API (D-05, API_CONTRACT Content rules): what is accepted and stored, what is refused with
 * {@code 400}, the 200 KB limit ({@code 413}), and the plain text and preview line the server derives. The rules
 * themselves are in {@link EntryContentValidatorTest}; this checks that they are wired to create and update, that a
 * refused caption saves nothing, and that no answer repeats what the client sent.
 */
class EntryContentTest extends EntryApiTest {

    private static final String PROBLEM_FIELD = "contentJson";

    // Accepted

    @Test
    void aCaptionIsStoredAsJsonWithTheTextAndPreviewTheServerDerivedFromIt() {
        User alice = aUser();
        Map<String, Object> caption = json(
                "type",
                "doc",
                "content",
                List.of(
                        heading(2, "What triggered the spiral?"),
                        paragraph("A message I left on read."),
                        paragraph("Then a long walk.")));

        Map<String, Object> entry = createEntry(alice, json("contentJson", caption));

        assertThat(entry).containsEntry("contentJson", caption);
        assertThat(entry)
                .containsEntry(
                        "contentText", "What triggered the spiral?\nA message I left on read.\nThen a long walk.");
        Map<String, Object> row = row(entry.get("id"));
        assertThat(row)
                .containsEntry(
                        "content_text", "What triggered the spiral?\nA message I left on read.\nThen a long walk.")
                .containsEntry("preview_line", "A message I left on read.");
        assertThat(JsonPath.<Object>read(row.get("content_json").toString(), "$.type"))
                .isEqualTo("doc");
        assertThat(getEntry(alice, entry.get("id"))).containsEntry("contentJson", caption);
    }

    @Test
    void anEntryWithOnlyHeadingsHasTheFirstHeadingAsItsPreview() {
        User alice = aUser();

        String id = createEntryId(
                alice, json("contentJson", json("type", "doc", "content", List.of(heading(1, "Night out")))));

        assertThat(row(id)).containsEntry("preview_line", "Night out");
    }

    @Test
    void thePreviewLineIsCutTo120Characters() {
        User alice = aUser();

        String id = createEntryId(alice, json("contentJson", doc("x".repeat(500))));

        assertThat(row(id).get("preview_line").toString()).hasSize(120);
        assertThat(row(id).get("content_text").toString()).hasSize(500);
    }

    @Test
    void markedUpTextListsAndLinksAreAccepted() {
        User alice = aUser();
        Map<String, Object> caption = json(
                "type",
                "doc",
                "content",
                List.of(
                        json(
                                "type",
                                "paragraph",
                                "content",
                                List.of(
                                        json("type", "text", "text", "Booked "),
                                        json(
                                                "type",
                                                "text",
                                                "text",
                                                "the trip",
                                                "marks",
                                                List.of(
                                                        json("type", "bold"),
                                                        json(
                                                                "type",
                                                                "link",
                                                                "attrs",
                                                                json("href", "https://example.com/trip?a=1&b=2")))),
                                        json("type", "hardBreak"),
                                        json(
                                                "type",
                                                "text",
                                                "text",
                                                "mail me",
                                                "marks",
                                                List.of(json(
                                                        "type",
                                                        "link",
                                                        "attrs",
                                                        json("href", "mailto:me@example.com")))))),
                        json(
                                "type",
                                "bulletList",
                                "content",
                                List.of(json("type", "listItem", "content", List.of(paragraph("tent")))))));

        Map<String, Object> entry = createEntry(alice, json("contentJson", caption));

        assertThat(entry).containsEntry("contentJson", caption);
        assertThat(entry).containsEntry("contentText", "Booked the trip\nmail me\ntent");
    }

    @Test
    void emojiAccentsAndNonLatinTextRoundTrip() {
        User alice = aUser();
        String words = "Birthday 🎉 at café, 日本語, <b>not bold</b> & \"quoted\"";

        Map<String, Object> entry = createEntry(alice, json("contentJson", doc(words)));

        assertThat(entry).containsEntry("contentText", words);
        assertThat(getEntry(alice, entry.get("id"))).containsEntry("contentText", words);
    }

    @Test
    void anEmptyDocumentIsAcceptedAndHasNoText() {
        User alice = aUser();

        Map<String, Object> entry = createEntry(alice, json("contentJson", json("type", "doc")));

        assertThat(entry).containsEntry("contentText", "");
        assertThat(row(entry.get("id")).get("preview_line")).isNull();
    }

    @Test
    void theClientNeverSuppliesTheTextOrThePreview() {
        User alice = aUser();

        Map<String, Object> entry = createEntry(
                alice,
                json(
                        "contentJson", doc("Real words"),
                        "contentText", "Words nobody typed",
                        "previewLine", "A preview nobody typed"));

        assertThat(entry).containsEntry("contentText", "Real words");
        assertThat(row(entry.get("id"))).containsEntry("preview_line", "Real words");
    }

    // Refused: nothing is saved, nothing is repeated

    @Test
    void aNodeThatIsNotOnTheListIs400WithTheFieldNamedAndNothingSaved() {
        User alice = aUser();
        Map<String, Object> caption =
                json("type", "doc", "content", List.of(paragraph("fine"), json("type", "supersecretnode")));

        String body = bodyText(post(alice, json("title", "Won't be saved", "contentJson", caption))
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsOnlyKeys(PROBLEM_FIELD);
        assertThat(fieldErrors(body).get(PROBLEM_FIELD))
                .isEqualTo("has a node type that is not allowed (at content[1])");
        assertThat(body).doesNotContain("supersecretnode").doesNotContain("Won't be saved");
        assertThat(countEntries(alice)).isZero();
        assertThat(countWeeks(alice)).isZero();
    }

    @Test
    void imagesCodeBlocksAndTablesAreRefusedUntilSomeoneDecidesToAllowThem() {
        User alice = aUser();

        for (String type : List.of("image", "codeBlock", "table", "taskList", "mention")) {
            post(alice, json("contentJson", json("type", "doc", "content", List.of(json("type", type)))))
                    .expectStatus()
                    .isBadRequest();
        }
        assertThat(countEntries(alice)).isZero();
    }

    @Test
    void aJavascriptLinkIs400NotStoredAndNotEchoed() {
        User alice = aUser();

        for (String href : List.of(
                "javascript:alert(document.cookie)",
                "JaVaScRiPt:alert(1)",
                " javascript:alert(1)",
                "java\tscript:alert(1)",
                "data:text/html,<script>alert(1)</script>",
                "//evil.example/x",
                "evil.example")) {
            Map<String, Object> link = json("type", "link", "attrs", json("href", href));
            Map<String, Object> text = json("type", "text", "text", "click me", "marks", List.of(link));
            Map<String, Object> caption =
                    json("type", "doc", "content", List.of(json("type", "paragraph", "content", List.of(text))));

            String body = bodyText(
                    post(alice, json("contentJson", caption)).expectStatus().isBadRequest());

            assertThat(problemType(body)).as(href).isEqualTo("validation-failed");
            assertThat(fieldErrors(body).get(PROBLEM_FIELD))
                    .as(href)
                    .isEqualTo(
                            "has a link that is not an http, https or mailto address (at content[0].content[0].marks[0])");
            assertThat(body)
                    .as("the rejected address is not echoed")
                    .doesNotContain("alert")
                    .doesNotContain("evil.example");
        }
        assertThat(countEntries(alice)).isZero();
    }

    @Test
    void aHeadingLevelOfFourIs400() {
        User alice = aUser();

        post(alice, json("contentJson", json("type", "doc", "content", List.of(heading(4, "Too deep")))))
                .expectStatus()
                .isBadRequest();
        createEntry(alice, json("contentJson", json("type", "doc", "content", List.of(heading(3, "Deep enough")))));
    }

    @Test
    void aCaptionThatIsNotADocumentIs400() {
        User alice = aUser();

        for (Object notADoc : List.of("just text", 42, true, List.of(), json(), json("type", "paragraph"))) {
            String body = bodyText(
                    post(alice, json("contentJson", notADoc)).expectStatus().isBadRequest());

            assertThat(fieldErrors(body).get(PROBLEM_FIELD))
                    .as(String.valueOf(notADoc))
                    .startsWith("must be a TipTap document");
        }
        assertThat(countEntries(alice)).isZero();
    }

    // The size limit

    @Test
    void aCaptionOfExactly200KbIsAcceptedAndOneByteMoreIs413() {
        User alice = aUser();
        // {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"..."}]}]} is 85 bytes plus the
        // text.
        int frame = 85;

        String atTheLimit = createEntryId(alice, json("contentJson", doc("a".repeat(200 * 1024 - frame))));
        assertThat(row(atTheLimit).get("content_text").toString()).hasSize(200 * 1024 - frame);

        String body =
                bodyText(post(alice, json("title", "Too big", "contentJson", doc("a".repeat(200 * 1024 - frame + 1))))
                        .expectStatus()
                        .isEqualTo(413));
        assertThat(problemType(body)).isEqualTo("payload-too-large");
        assertThat(JsonPath.<Integer>read(body, "$.status")).isEqualTo(413);
        assertThat(body).doesNotContain("aaaaa").doesNotContain("Too big");
        assertThat(countEntries(alice)).as("only the one at the limit").isEqualTo(1);
    }

    @Test
    void anUpdateWithATooLargeCaptionIs413AndChangesNothing() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Small", "contentJson", doc("small")));

        put(
                        alice,
                        id,
                        json(
                                "version",
                                0,
                                "entryDate",
                                "2026-10-31",
                                "title",
                                "Big",
                                "contentJson",
                                doc("a".repeat(300 * 1024))))
                .expectStatus()
                .isEqualTo(413);

        assertThat(row(id))
                .containsEntry("title", "Small")
                .containsEntry("content_text", "small")
                .containsEntry("version", 0L);
    }

    @Test
    void aDocumentNestedAbsurdlyDeepIsRefusedWhileItIsReadAndNeverBecomesAServerError() {
        User alice = aUser();
        int depth = 5_000;
        String opening = "{\"type\":\"blockquote\",\"content\":[".repeat(depth);
        String closing = "]}".repeat(depth);

        String body =
                bodyText(postRaw(alice, "{\"contentJson\":{\"type\":\"doc\",\"content\":[" + opening + closing + "]}}")
                        .expectStatus()
                        .isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
        assertThat(countEntries(alice)).isZero();
    }

    @Test
    void aBodyThatIsNotJsonButSaysItIsNeverReachesTheValidator() {
        User alice = aUser();

        String body = bodyText(client.post()
                .uri(ENTRIES)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"contentJson\": {\"type\": \"doc\", \"content\": [")
                .exchange()
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
    }

    // Update

    @Test
    void anUpdateChecksTheCaptionLikeACreateAndSavesNothingWhenItIsRefused() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Keep me", "contentJson", doc("keep")));
        Map<String, Object> bad = json("type", "doc", "content", List.of(json("type", "image")));

        String body = bodyText(
                put(alice, id, json("version", 0, "entryDate", "2026-10-31", "title", "Changed", "contentJson", bad))
                        .expectStatus()
                        .isBadRequest());

        assertThat(fieldErrors(body)).containsOnlyKeys(PROBLEM_FIELD);
        assertThat(row(id))
                .containsEntry("title", "Keep me")
                .containsEntry("content_text", "keep")
                .containsEntry("version", 0L);
    }

    @Test
    void anUpdateReplacesTheCaptionAndTheDerivedTextAndPreview() {
        User alice = aUser();
        String id = createEntryId(alice, json("contentJson", doc("old words")));

        Map<String, Object> edited = bodyMap(
                put(alice, id, json("version", 0, "entryDate", "2026-10-31", "contentJson", doc("new words", "more")))
                        .expectStatus()
                        .isOk());

        assertThat(edited).containsEntry("contentText", "new words\nmore");
        assertThat(row(id)).containsEntry("content_text", "new words\nmore").containsEntry("preview_line", "new words");
    }

    @Test
    void anUpdateWithNoCaptionClearsTheCaptionTheTextAndThePreview() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Titled", "contentJson", doc("old words")));

        Map<String, Object> edited =
                bodyMap(put(alice, id, json("version", 0, "entryDate", "2026-10-31", "title", "Titled"))
                        .expectStatus()
                        .isOk());

        assertThat(edited).containsEntry("contentJson", null).containsEntry("contentText", "");
        assertThat(row(id))
                .containsEntry("content_json", null)
                .containsEntry("content_text", "")
                .containsEntry("preview_line", null);
    }
}
