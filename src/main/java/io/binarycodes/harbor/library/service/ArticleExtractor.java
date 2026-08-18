package io.binarycodes.harbor.library.service;

import java.util.Set;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Turns a fetched page into the Markdown the reader shows. Only the parts of a
 * page that carry the article are kept — chrome, navigation, and scripts are
 * dropped — and the result is Markdown rather than HTML so the reader can render
 * it through the same sanitised component as the reader's own notes.
 *
 * <p>Which part of the page is the article is {@link ArticleContent}'s decision;
 * this turns whatever that hands back into prose.
 */
public final class ArticleExtractor {

    private static final int SHORTEST_USEFUL_ARTICLE = 200;

    private static final String BLOCK_SELECTOR = "h1, h2, h3, h4, p, ul, ol, blockquote, pre";
    private static final Set<String> BLOCK_TAGS =
            Set.of("h1", "h2", "h3", "h4", "p", "ul", "ol", "blockquote", "pre");

    /**
     * Only the web's own two schemes become links. A saved page is untrusted input,
     * and a {@code javascript:} href dressed up as a citation is not something to
     * hand the reader as clickable.
     */
    private static final Pattern LINKABLE_SCHEME = Pattern.compile("(?i)^https?://");

    private ArticleExtractor() {
    }

    /**
     * @return the article as Markdown, or an empty string when the page has too
     *         little prose to be worth showing in the reader
     */
    public static String toMarkdown(Document document) {
        Element root = ArticleContent.cleaned(document);
        if (root == null) {
            return "";
        }
        StringBuilder markdown = new StringBuilder();
        for (Element block : root.select(BLOCK_SELECTOR)) {
            if (isNested(block)) {
                continue;
            }
            append(markdown, block);
        }
        String result = markdown.toString().strip();
        return result.length() < SHORTEST_USEFUL_ARTICLE ? "" : result;
    }

    /**
     * A paragraph inside a blockquote, or a list inside a list item, is rendered by
     * its outermost block — visiting it again would repeat the text.
     */
    private static boolean isNested(Element block) {
        return block.parents().stream().anyMatch(parent -> BLOCK_TAGS.contains(parent.tagName()));
    }

    private static void append(StringBuilder markdown, Element block) {
        if ("pre".equals(block.tagName())) {
            // Code is kept literally; a link inside it is part of the sample.
            String code = block.wholeText().strip();
            if (!code.isEmpty()) {
                markdown.append("```\n").append(code).append("\n```\n\n");
            }
            return;
        }
        String text = inline(block);
        if (text.isEmpty()) {
            return;
        }
        switch (block.tagName()) {
            case "h1", "h2" -> markdown.append("## ").append(text).append("\n\n");
            case "h3", "h4" -> markdown.append("### ").append(text).append("\n\n");
            case "blockquote" -> markdown.append("> ").append(text).append("\n\n");
            case "ul" -> appendList(markdown, block, false);
            case "ol" -> appendList(markdown, block, true);
            default -> markdown.append(text).append("\n\n");
        }
    }

    private static void appendList(StringBuilder markdown, Element list, boolean numbered) {
        int position = 1;
        for (Element item : list.select("> li")) {
            String text = inline(item);
            if (text.isEmpty()) {
                continue;
            }
            markdown.append(numbered ? position++ + ". " : "- ").append(text).append('\n');
        }
        markdown.append('\n');
    }

    /**
     * A block's text with its links kept as Markdown. Anything else inline is
     * flattened to plain text — what a reader of a saved article needs back is
     * where it pointed, not how it was emphasised.
     */
    private static String inline(Element block) {
        StringBuilder text = new StringBuilder();
        appendInline(block, text);
        return text.toString().replaceAll("\\s+", " ").strip();
    }

    private static void appendInline(Node node, StringBuilder text) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode textNode) {
                text.append(textNode.text());
            } else if (child instanceof Element element) {
                if ("a".equals(element.tagName())) {
                    appendLink(element, text);
                } else {
                    appendInline(element, text);
                }
            }
        }
    }

    private static void appendLink(Element anchor, StringBuilder text) {
        String label = inline(anchor);
        if (label.isEmpty()) {
            return;
        }
        // absUrl resolves the page's relative hrefs; it is empty when the href is
        // relative and the document has no base, which is nothing we can link to.
        String href = anchor.absUrl("href");
        if (!LINKABLE_SCHEME.matcher(href).find()) {
            text.append(label);
            return;
        }
        text.append('[').append(label.replace("]", "\\]")).append(']')
                .append('(').append(needsAngleBrackets(href) ? "<" + href + ">" : href).append(')');
    }

    private static boolean needsAngleBrackets(String href) {
        return href.indexOf(' ') >= 0 || href.indexOf('(') >= 0 || href.indexOf(')') >= 0;
    }
}
