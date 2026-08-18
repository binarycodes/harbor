package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.binarycodes.harbor.library.domain.BookmarkType;

@DisplayName("Reading a page to describe a link")
class OpenGraphMetadataResolverTest {

    private static final String BODY = "<article><p>" + "word ".repeat(400) + "</p></article>";

    @Nested
    @DisplayName("what the page says about itself")
    class PageMetadata {

        @Test
        @DisplayName("is taken from the Open Graph tags first")
        void prefersOpenGraph() {
            LinkMetadata metadata = resolve("""
                    <html><head>
                      <title>Ignored fallback title</title>
                      <meta property="og:site_name" content="Josh W. Comeau">
                      <meta property="og:title" content="An Interactive Guide to Flexbox">
                      <meta property="og:description" content="How the algorithm actually works.">
                    </head><body>%s</body></html>
                    """.formatted(BODY));

            assertEquals("Josh W. Comeau", metadata.site());
            assertEquals("An Interactive Guide to Flexbox", metadata.title());
            assertEquals("How the algorithm actually works.", metadata.description());
        }

        @Test
        @DisplayName("falls back to the document title and meta description")
        void fallsBackToDocumentTags() {
            LinkMetadata metadata = resolve("""
                    <html><head>
                      <title>The document title</title>
                      <meta name="description" content="The plain description.">
                    </head><body>%s</body></html>
                    """.formatted(BODY));

            assertEquals("The document title", metadata.title());
            assertEquals("The plain description.", metadata.description());
            assertEquals("example.com", metadata.site());
        }

        @Test
        @DisplayName("falls back to the URL when the page says nothing")
        void fallsBackToTheUrl() {
            LinkMetadata metadata = resolve("<html><head></head><body>%s</body></html>".formatted(BODY));

            assertEquals("example.com", metadata.site());
            assertEquals("Some Article", metadata.title());
            assertTrue(metadata.description().isEmpty());
        }

        @Test
        @DisplayName("honours the Open Graph kinds Harbor distinguishes")
        void readsOpenGraphType() {
            assertEquals(BookmarkType.VIDEO, resolve("""
                    <html><head><meta property="og:type" content="video.other"></head>
                    <body>%s</body></html>
                    """.formatted(BODY)).type());
            assertEquals(BookmarkType.BOOK, resolve("""
                    <html><head><meta property="og:type" content="book"></head>
                    <body>%s</body></html>
                    """.formatted(BODY)).type());
            assertEquals(BookmarkType.ARTICLE, resolve("""
                    <html><head><meta property="og:type" content="website"></head>
                    <body>%s</body></html>
                    """.formatted(BODY)).type());
        }
    }

    @Nested
    @DisplayName("the article for the reader")
    class ArticleBody {

        @Test
        @DisplayName("is extracted along with an estimate of how long it takes")
        void extractsBodyAndReadingTime() {
            LinkMetadata metadata = resolve("<html><head></head><body>%s</body></html>".formatted(BODY));

            assertTrue(metadata.content().contains("word word"));
            assertEquals(2, metadata.readingMinutes());
        }
    }

    @Nested
    @DisplayName("when the page cannot be read")
    class Unreachable {

        @Test
        @DisplayName("the link is still described from its URL")
        void fallsBackToTheHeuristic() {
            OpenGraphMetadataResolver resolver = resolverOver(url -> {
                throw new IOException("refused");
            });

            LinkMetadata metadata = resolver.resolve("https://arxiv.org/abs/1706.03762");

            assertEquals("arxiv.org", metadata.site());
            assertEquals(BookmarkType.PAPER, metadata.type());
            assertEquals(List.of("Research", "AI"), metadata.tags());
            assertTrue(metadata.content().isEmpty());
        }

        /**
         * The description is a consolation prize, not a read. Saying so is what lets the
         * dialog refuse to file a link whose page nobody could reach.
         */
        @Test
        @DisplayName("says the page was not read")
        void reportsThePageWasNotRead() {
            OpenGraphMetadataResolver resolver = resolverOver(url -> {
                throw new IOException("refused");
            });

            assertFalse(resolver.resolve("https://arxiv.org/abs/1706.03762").pageRead());
        }

        @Test
        @DisplayName("but says it was when the page came back")
        void reportsThePageWasRead() {
            assertTrue(resolve("<html><head><title>Read</title></head><body>%s</body></html>"
                    .formatted(BODY)).pageRead());
        }
    }

    @Nested
    @DisplayName("the URL itself")
    class UrlHandling {

        @Test
        @DisplayName("gets https assumed when a scheme was left out")
        void assumesHttps() {
            assertEquals("https://example.com/thing",
                    OpenGraphMetadataResolver.absolute("example.com/thing"));
        }

        @Test
        @DisplayName("is left alone when it already has a scheme")
        void keepsExistingScheme() {
            assertEquals("http://example.com/thing",
                    OpenGraphMetadataResolver.absolute("http://example.com/thing"));
        }

        @Test
        @DisplayName("is refused for anything the server has no business fetching")
        void refusesOtherSchemes() {
            assertThrows(IllegalArgumentException.class,
                    () -> OpenGraphMetadataResolver.absolute("file:///etc/passwd"));
            assertThrows(IllegalArgumentException.class,
                    () -> OpenGraphMetadataResolver.absolute("jar:file:///tmp/x.jar!/y"));
            assertThrows(IllegalArgumentException.class,
                    () -> OpenGraphMetadataResolver.absolute("https://"));
            assertThrows(IllegalArgumentException.class,
                    () -> OpenGraphMetadataResolver.absolute("https://exa mple.com"));
        }
    }

    private LinkMetadata resolve(String html) {
        OpenGraphMetadataResolver resolver =
                resolverOver(url -> Jsoup.parse(html, url));
        return resolver.resolve("https://example.com/some-article");
    }

    /**
     * Archiving is exercised by {@link ArticlePdfRendererTest}; here it is stubbed
     * out so these tests stay about what a page says of itself.
     */
    private static OpenGraphMetadataResolver resolverOver(DocumentLoader loader) {
        return new OpenGraphMetadataResolver(loader,
                (document, title, url, archivedAt) -> java.util.Optional.empty(),
                java.time.Clock.systemUTC());
    }
}
