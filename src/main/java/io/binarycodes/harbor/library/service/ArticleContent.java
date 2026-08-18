package io.binarycodes.harbor.library.service;

import java.util.Comparator;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Finding the article in a page and throwing away everything around it.
 *
 * <p>Deliberately a plain structural extraction rather than a readability score:
 * it prefers the page's own {@code <article>} or {@code <main>} landmark, and only
 * falls back to picking the densest block of prose when a page offers neither.
 *
 * <p>Split out from {@link ArticleExtractor} because the archive needs the same
 * decision. What counts as the article should not be one thing for the reader's
 * text and another for the PDF kept alongside it.
 */
final class ArticleContent {

    private static final String NOISE = "script, style, noscript, nav, aside, footer, header, form,"
            + " iframe, svg, button, figure figcaption";
    private static final List<String> CONTENT_ROOTS = List.of("article", "[role=main]", "main");

    private ArticleContent() {
    }

    /**
     * The article's own element, cleaned of chrome, or {@code null} when there is no
     * document to read. The document is copied first, so a caller that goes on to
     * read the page for something else still sees all of it.
     */
    static Element cleaned(Document document) {
        if (document == null) {
            return null;
        }
        Element root = contentRoot(document.clone());
        if (root == null) {
            return null;
        }
        root.select(NOISE).remove();
        return root;
    }

    private static Element contentRoot(Document document) {
        for (String selector : CONTENT_ROOTS) {
            Element candidate = document.selectFirst(selector);
            if (candidate != null) {
                return candidate;
            }
        }
        return densestBlock(document);
    }

    /**
     * The element whose own paragraphs carry the most text. Pages without a
     * landmark still tend to keep the article in one container.
     */
    private static Element densestBlock(Document document) {
        Elements candidates = document.select("div, section, td");
        return candidates.stream()
                .max(Comparator.comparingInt(ArticleContent::directParagraphLength))
                .filter(candidate -> directParagraphLength(candidate) > 0)
                .orElse(document.body());
    }

    private static int directParagraphLength(Element element) {
        return element.children().stream()
                .filter(child -> "p".equals(child.tagName()))
                .mapToInt(child -> child.text().length())
                .sum();
    }
}
