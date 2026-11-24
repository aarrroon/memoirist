package com.memoirist.entry;

import com.memoirist.common.PayloadTooLargeException;
import com.memoirist.common.ValidationFailedException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Checks that an entry's caption is a safe TipTap document before it is stored (D-05, API_CONTRACT, Content rules).
 *
 * <p>Nothing here parses HTML or renders anything. The caption is JSON, the frontend draws it from the JSON, and
 * this class is what stops the JSON from carrying something that a renderer, a share page (Phase 2) or an AI prompt
 * (Phase 4) would later trust. The rules are an <em>allow-list</em>: anything not named here is refused, so a
 * new TipTap extension on the client is rejected until somebody decides to allow it.
 *
 * <ul>
 *   <li>The document is at most {@value #MAX_BYTES} bytes once serialised ({@code 413 payload-too-large}).
 *   <li>The root is {@code {"type": "doc"}} and every node is an object with an allowed {@code type}.
 *   <li>A node has only the fields {@code type}, {@code content}, {@code attrs}, {@code marks} and {@code text}, and
 *       a leaf ({@code text}, {@code hardBreak}, {@code horizontalRule}) has no {@code content}.
 *   <li>A {@code heading} has {@code attrs.level} 1, 2 or 3. Other attributes are kept as sent: nothing reads them
 *       except the renderer, which looks only at the ones it knows.
 *   <li>A mark is {@code bold}, {@code italic}, {@code strike}, {@code code} or {@code link}, and a link's
 *       {@code attrs.href} passes {@link #isAllowedLink}.
 * </ul>
 *
 * Every other failure is {@code 400 validation-failed} on the field {@value #FIELD}. A message says what is wrong and
 * where ({@code content[1].content[0]}), and never repeats what the client sent: a rejected node type, text or address
 * is untrusted input (API_CONTRACT, Errors).
 *
 * <p>The nesting depth is not limited here. Jackson refuses a document nested more than 500 levels while it reads the
 * request ({@code 400 malformed-request}), which keeps these recursive walks far from a stack overflow.
 */
public final class EntryContentValidator {

    /** 200 KB (D-05), counted as 200 x 1024 bytes of compact UTF-8 JSON. */
    public static final int MAX_BYTES = 200 * 1024;

    /** The name of the request field, as it appears in {@code fieldErrors}. */
    public static final String FIELD = "contentJson";

    private static final Set<String> NODE_TYPES = Set.of(
            "doc",
            "paragraph",
            "text",
            "heading",
            "bulletList",
            "orderedList",
            "listItem",
            "blockquote",
            "hardBreak",
            "horizontalRule");
    private static final Set<String> LEAF_TYPES = Set.of("text", "hardBreak", "horizontalRule");
    private static final Set<String> MARK_TYPES = Set.of("bold", "italic", "strike", "code", "link");
    private static final Set<String> NODE_FIELDS = Set.of("type", "content", "attrs", "marks", "text");
    private static final Set<String> MARK_FIELDS = Set.of("type", "attrs");

    private EntryContentValidator() {}

    /**
     * Throws if {@code doc} may not be stored as a caption. A caller passes only a document that is there: no caption
     * at all is {@code null} and needs no check.
     *
     * @throws PayloadTooLargeException if it is over {@value #MAX_BYTES} bytes
     * @throws ValidationFailedException if it breaks any other rule above
     */
    public static void validate(JsonNode doc) {
        // Size first: it is one pass over the serialised text and refuses a huge document before the walk below.
        if (doc.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new PayloadTooLargeException("The content is larger than 200 KB.");
        }
        if (!doc.isObject() || !isType(doc, "doc")) {
            throw invalid("must be a TipTap document: an object whose type is doc");
        }
        checkNode(doc, "the document", true);
    }

    private static void checkNode(JsonNode node, String path, boolean root) {
        if (!node.isObject()) {
            throw invalid("has a node that is not an object (at " + path + ")");
        }
        String type = textOf(node.get("type"));
        // The root is the only "doc", so a doc nested inside a document is refused with the other unknown types.
        if (type == null || !NODE_TYPES.contains(type) || ("doc".equals(type) && !root)) {
            throw invalid("has a node type that is not allowed (at " + path + ")");
        }
        for (String field : node.propertyNames()) {
            if (!NODE_FIELDS.contains(field)) {
                throw invalid("has a node field that is not allowed (at " + path + ")");
            }
        }

        if ("text".equals(type) && textOf(node.get("text")) == null) {
            throw invalid("has a text node without text (at " + path + ")");
        }
        if (!"text".equals(type) && present(node, "text")) {
            throw invalid("has text on a node that is not a text node (at " + path + ")");
        }
        if (present(node, "attrs") && !node.get("attrs").isObject()) {
            throw invalid("has node attributes that are not an object (at " + path + ")");
        }
        if ("heading".equals(type) && !hasAllowedHeadingLevel(node)) {
            throw invalid("has a heading level that is not 1, 2 or 3 (at " + path + ")");
        }

        if (present(node, "marks")) {
            checkMarks(node.get("marks"), path + ".marks");
        }
        if (present(node, "content")) {
            JsonNode children = node.get("content");
            if (!children.isArray() || LEAF_TYPES.contains(type)) {
                throw invalid("has content where it is not allowed (at " + path + ")");
            }
            for (int i = 0; i < children.size(); i++) {
                checkNode(children.get(i), childPath(path, i), false);
            }
        }
    }

    private static void checkMarks(JsonNode marks, String path) {
        if (!marks.isArray()) {
            throw invalid("has marks that are not a list (at " + path + ")");
        }
        for (int i = 0; i < marks.size(); i++) {
            JsonNode mark = marks.get(i);
            String markPath = path + "[" + i + "]";
            if (!mark.isObject()) {
                throw invalid("has a mark that is not an object (at " + markPath + ")");
            }
            String type = textOf(mark.get("type"));
            if (type == null || !MARK_TYPES.contains(type)) {
                throw invalid("has a mark that is not allowed (at " + markPath + ")");
            }
            for (String field : mark.propertyNames()) {
                if (!MARK_FIELDS.contains(field)) {
                    throw invalid("has a mark field that is not allowed (at " + markPath + ")");
                }
            }
            if (present(mark, "attrs") && !mark.get("attrs").isObject()) {
                throw invalid("has mark attributes that are not an object (at " + markPath + ")");
            }
            if ("link".equals(type)) {
                String href = present(mark, "attrs") ? textOf(mark.get("attrs").get("href")) : null;
                if (href == null || !isAllowedLink(href)) {
                    throw invalid("has a link that is not an http, https or mailto address (at " + markPath + ")");
                }
            }
        }
    }

    // TODO(human) PRACTICE: Write isAllowedLink(href): true only for an address that is safe to store behind a link.
    //   It must accept https://example.com/a?b=c, HTTP://EXAMPLE.COM and mailto:me@example.com, and refuse
    //   javascript:alert(1), JavaScript:alert(1), data:text/html;base64,..., //evil.example, /relative and a bare
    //   example.com. Then break it: " javascript:alert(1)" and "java\tscript:alert(1)" both run script in a browser.
    // Hint: D-05 and .claude/rules/security.md (Content). Browsers trim spaces at both ends and delete tabs and
    //   line breaks anywhere in an address before reading its scheme, so a check that looks at the raw text is
    //   fooled. An allow-list of what may start the address is safer than a block-list of what may not. Refusing
    //   every control character removes the whole "java<TAB>script:" family of tricks.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    static boolean isAllowedLink(String href) {
        String address = href.strip();
        if (address.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        String lower = address.toLowerCase(Locale.ROOT);
        for (String prefix : new String[] {"http://", "https://", "mailto:"}) {
            if (lower.startsWith(prefix)) {
                return lower.length() > prefix.length();
            }
        }
        return false;
    }
    // └── END ANSWER ▲ ──

    private static boolean hasAllowedHeadingLevel(JsonNode heading) {
        JsonNode attrs = heading.get("attrs");
        JsonNode level = attrs == null ? null : attrs.get("level");
        return level != null
                && level.isIntegralNumber()
                && level.canConvertToInt()
                && level.intValue() >= 1
                && level.intValue() <= 3;
    }

    private static boolean isType(JsonNode node, String type) {
        return type.equals(textOf(node.get("type")));
    }

    /** The node's text if it is a JSON string, otherwise {@code null} (a missing field, a number, an object). */
    private static String textOf(JsonNode node) {
        return node != null && node.isString() ? node.stringValue() : null;
    }

    /** True when the field is there and is not JSON {@code null}: a {@code null} counts as absent. */
    private static boolean present(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull();
    }

    private static String childPath(String parentPath, int index) {
        String prefix = parentPath.equals("the document") ? "" : parentPath + ".";
        return prefix + "content[" + index + "]";
    }

    private static ValidationFailedException invalid(String message) {
        return new ValidationFailedException(FIELD, message);
    }
}
