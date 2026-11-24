package com.memoirist.entry;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Turns a TipTap document into the two plain-text values the server stores next to it (D-05, D-24):
 *
 * <ul>
 *   <li>{@code content_text}: the words of the caption, one line for each paragraph, heading, list item and
 *       {@code hardBreak}. Formatting is gone. AI, search and previews read this, never the JSON.
 *   <li>{@code preview_line}: the first line that isn't a heading, or the first heading if the caption has nothing
 *       else, cut to {@value #MAX_PREVIEW_CHARACTERS} characters. Skipping headings stops every entry started from a
 *       template being listed under its first prompt (D-24).
 * </ul>
 *
 * <p>It is a pure function of the document, written for a document that {@link EntryContentValidator} has already
 * accepted but safe on any shape: a field that isn't there or isn't what it should be simply adds nothing.
 */
public final class ContentTextExtractor {

    /** {@code entries.preview_line} is {@code VARCHAR(120)}. */
    public static final int MAX_PREVIEW_CHARACTERS = 120;

    private ContentTextExtractor() {}

    /**
     * What was taken from a caption.
     *
     * @param text the plain text, never {@code null}; empty when there is no caption or it holds no words
     * @param previewLine {@code null} when the caption has no line with any text in it
     */
    public record Extracted(String text, String previewLine) {}

    /** The plain text and preview line of {@code doc}. A {@code null} document (no caption) gives {@code ("", null)}. */
    public static Extracted extract(JsonNode doc) {
        Lines lines = new Lines();
        if (doc != null) {
            lines.walk(doc);
            lines.endLine();
        }
        return new Extracted(lines.text(), lines.previewLine());
    }

    /** One line of the caption, and whether it came from a heading. */
    private record Line(String text, boolean heading) {}

    /** Collects the lines while the document is walked. */
    private static final class Lines {

        private final List<Line> done = new ArrayList<>();
        private final StringBuilder current = new StringBuilder();

        /** True from the start of a paragraph or heading until its line ends, so an empty paragraph is an empty line. */
        private boolean open;

        private boolean headingLine;

        void walk(JsonNode node) {
            if (!node.isObject()) {
                return;
            }
            JsonNode typeNode = node.get("type");
            String type = typeNode != null && typeNode.isString() ? typeNode.stringValue() : "";
            switch (type) {
                case "text" -> appendText(textOf(node.get("text")));
                case "hardBreak" -> breakLine();
                case "paragraph" -> textBlock(node, false);
                case "heading" -> textBlock(node, true);
                case "horizontalRule" -> endLine();
                default -> {
                    // doc, lists, list items and quotes only hold other nodes: whatever came before ends its line.
                    endLine();
                    walkChildren(node);
                    endLine();
                }
            }
        }

        private void textBlock(JsonNode block, boolean heading) {
            endLine();
            open = true;
            headingLine = heading;
            walkChildren(block);
            endLine();
        }

        private void walkChildren(JsonNode node) {
            JsonNode children = node.get("content");
            if (children != null && children.isArray()) {
                children.forEach(this::walk);
            }
        }

        /** Text in a document is one run, but a stray line break inside it still has to start a new line. */
        private void appendText(String text) {
            String[] parts = text.split("\\R", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    breakLine();
                }
                current.append(parts[i]);
            }
        }

        /** A line break inside a paragraph or heading: the next words start a new line of the same kind. */
        private void breakLine() {
            boolean heading = headingLine;
            endLine();
            open = true;
            headingLine = heading;
        }

        private void endLine() {
            if (open || !current.isEmpty()) {
                done.add(new Line(current.toString(), headingLine));
            }
            current.setLength(0);
            open = false;
            headingLine = false;
        }

        String text() {
            return String.join("\n", done.stream().map(Line::text).toList()).strip();
        }

        String previewLine() {
            String firstHeading = null;
            for (Line line : done) {
                String text = line.text().strip();
                if (text.isEmpty()) {
                    continue;
                }
                if (!line.heading()) {
                    return truncate(text);
                }
                if (firstHeading == null) {
                    firstHeading = text;
                }
            }
            return firstHeading == null ? null : truncate(firstHeading);
        }

        /** At most 120 characters, counted as code points so an emoji is never cut in half. */
        private static String truncate(String text) {
            if (text.codePointCount(0, text.length()) <= MAX_PREVIEW_CHARACTERS) {
                return text;
            }
            return text.substring(0, text.offsetByCodePoints(0, MAX_PREVIEW_CHARACTERS))
                    .stripTrailing();
        }

        private static String textOf(JsonNode node) {
            return node != null && node.isString() ? node.stringValue() : "";
        }
    }
}
