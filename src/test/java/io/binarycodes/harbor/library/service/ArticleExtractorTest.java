package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Extracting an article from a page")
class ArticleExtractorTest {

    private static final String LONG_PARAGRAPH = "Flexbox is a remarkably powerful layout mode, and once "
            + "you understand how the algorithm distributes space the properties stop feeling arbitrary. "
            + "This paragraph is long enough that the extractor considers the page worth reading.";

    @Test
    @DisplayName("returns nothing for no page at all")
    void handlesNoDocument() {
        assertEquals("", ArticleExtractor.toMarkdown(null));
    }

    @Test
    @DisplayName("prefers the page's own article landmark")
    void prefersArticleLandmark() {
        String markdown = ArticleExtractor.toMarkdown(Jsoup.parse("""
                <html><body>
                  <nav><p>Home About Contact</p></nav>
                  <article><h1>The title</h1><p>%s</p></article>
                  <footer><p>Copyright somebody</p></footer>
                </body></html>
                """.formatted(LONG_PARAGRAPH)));

        assertTrue(markdown.startsWith("## The title"));
        assertTrue(markdown.contains("remarkably powerful layout mode"));
        assertFalse(markdown.contains("Home About Contact"));
        assertFalse(markdown.contains("Copyright somebody"));
    }

    @Test
    @DisplayName("falls back to main, and then to the densest block of prose")
    void fallsBackThroughLandmarks() {
        String fromMain = ArticleExtractor.toMarkdown(Jsoup.parse(
                "<html><body><main><p>%s</p></main></body></html>".formatted(LONG_PARAGRAPH)));
        assertTrue(fromMain.contains("remarkably powerful"));

        String fromDensestBlock = ArticleExtractor.toMarkdown(Jsoup.parse("""
                <html><body>
                  <div class="sidebar"><p>Short aside.</p></div>
                  <div class="content"><p>%s</p></div>
                </body></html>
                """.formatted(LONG_PARAGRAPH)));
        assertTrue(fromDensestBlock.contains("remarkably powerful"));
        assertFalse(fromDensestBlock.contains("Short aside"));
    }

    @Test
    @DisplayName("keeps headings, lists, quotes and code as Markdown")
    void mapsBlocksToMarkdown() {
        String markdown = ArticleExtractor.toMarkdown(Jsoup.parse("""
                <html><body><article>
                  <h2>Ideas</h2>
                  <h3>Detail</h3>
                  <p>%s</p>
                  <ul><li>First point</li><li>Second point</li></ul>
                  <ol><li>Step one</li><li>Step two</li></ol>
                  <blockquote>Worth remembering.</blockquote>
                  <pre>display: flex;</pre>
                </article></body></html>
                """.formatted(LONG_PARAGRAPH)));

        assertTrue(markdown.contains("## Ideas"));
        assertTrue(markdown.contains("### Detail"));
        assertTrue(markdown.contains("- First point"));
        assertTrue(markdown.contains("1. Step one"));
        assertTrue(markdown.contains("2. Step two"));
        assertTrue(markdown.contains("> Worth remembering."));
        assertTrue(markdown.contains("```"));
        assertTrue(markdown.contains("display: flex;"));
    }

    @Test
    @DisplayName("does not repeat a paragraph that sits inside a quote")
    void doesNotRepeatNestedBlocks() {
        String markdown = ArticleExtractor.toMarkdown(Jsoup.parse("""
                <html><body><article>
                  <p>%s</p>
                  <blockquote><p>Nested and quoted.</p></blockquote>
                </article></body></html>
                """.formatted(LONG_PARAGRAPH)));

        assertEquals(1, markdown.split("Nested and quoted", -1).length - 1);
        assertTrue(markdown.contains("> Nested and quoted."));
    }

    @Test
    @DisplayName("gives up on a page with too little prose to read")
    void givesUpOnThinPages() {
        String markdown = ArticleExtractor.toMarkdown(Jsoup.parse(
                "<html><body><article><p>Too short.</p></article></body></html>"));

        assertEquals("", markdown);
    }

    @Nested
    @DisplayName("links in the prose")
    class Links {

        @Test
        @DisplayName("are kept as Markdown rather than flattened away")
        void keepsLinks() {
            String markdown = ArticleExtractor.toMarkdown(Jsoup.parse("""
                    <html><body><article><p>%s
                      See <a href="https://vaadin.com/docs">the documentation</a> for more.
                    </p></article></body></html>
                    """.formatted(LONG_PARAGRAPH)));

            assertTrue(markdown.contains("[the documentation](https://vaadin.com/docs)"));
        }

        @Test
        @DisplayName("are resolved against the page they came from")
        void resolvesRelativeLinks() {
            String markdown = ArticleExtractor.toMarkdown(Jsoup.parse("""
                    <html><body><article><p>%s
                      See <a href="/docs/latest">the docs</a>.
                    </p></article></body></html>
                    """.formatted(LONG_PARAGRAPH), "https://vaadin.com/blog/some-post"));

            assertTrue(markdown.contains("[the docs](https://vaadin.com/docs/latest)"));
        }

        @Test
        @DisplayName("survive inside headings and list items")
        void keepsLinksInsideOtherBlocks() {
            String markdown = ArticleExtractor.toMarkdown(Jsoup.parse("""
                    <html><body><article>
                      <h2>See <a href="https://example.com/spec">the spec</a></h2>
                      <p>%s</p>
                      <ul><li>Read <a href="https://example.com/guide">the guide</a></li></ul>
                    </article></body></html>
                    """.formatted(LONG_PARAGRAPH)));

            assertTrue(markdown.contains("## See [the spec](https://example.com/spec)"));
            assertTrue(markdown.contains("- Read [the guide](https://example.com/guide)"));
        }

        @Test
        @DisplayName("keep their label but lose the href when it is not a web address")
        void refusesNonWebSchemes() {
            String markdown = ArticleExtractor.toMarkdown(Jsoup.parse("""
                    <html><body><article><p>%s
                      <a href="javascript:steal()">Click here</a>
                      <a href="mailto:someone@example.com">Mail us</a>
                    </p></article></body></html>
                    """.formatted(LONG_PARAGRAPH)));

            assertTrue(markdown.contains("Click here"));
            assertTrue(markdown.contains("Mail us"));
            assertFalse(markdown.contains("javascript:"));
            assertFalse(markdown.contains("mailto:"));
        }

        @Test
        @DisplayName("are left as plain text when a relative href cannot be resolved")
        void keepsUnresolvableLinksAsText() {
            String markdown = ArticleExtractor.toMarkdown(Jsoup.parse("""
                    <html><body><article><p>%s
                      <a href="/docs">the docs</a>
                    </p></article></body></html>
                    """.formatted(LONG_PARAGRAPH)));

            assertTrue(markdown.contains("the docs"));
            assertFalse(markdown.contains("]("));
        }

        @Test
        @DisplayName("do not become links when the anchor has nothing to click")
        void skipsEmptyAnchors() {
            String markdown = ArticleExtractor.toMarkdown(Jsoup.parse("""
                    <html><body><article><p>%s<a href="https://example.com"></a></p></article></body></html>
                    """.formatted(LONG_PARAGRAPH)));

            assertFalse(markdown.contains("]("));
        }
    }

    @Test
    @DisplayName("drops scripts and styles rather than reading them aloud")
    void dropsNoise() {
        String markdown = ArticleExtractor.toMarkdown(Jsoup.parse("""
                <html><body><article>
                  <script>const secret = 1;</script>
                  <style>.thing { color: red; }</style>
                  <p>%s</p>
                </article></body></html>
                """.formatted(LONG_PARAGRAPH)));

        assertFalse(markdown.contains("const secret"));
        assertFalse(markdown.contains("color: red"));
    }
}
