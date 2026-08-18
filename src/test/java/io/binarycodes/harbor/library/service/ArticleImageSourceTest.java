package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Where an image really lives. The cases here are the shapes the web actually
 * ships — see docs/issues/002 — and each one would otherwise archive as blank
 * space with nothing logged.
 */
@DisplayName("Finding the image behind an img tag")
class ArticleImageSourceTest {

    private static final String PAGE = "https://example.com/article/one";

    @Nested
    @DisplayName("when the page loads its images eagerly")
    class Eager {

        @Test
        @DisplayName("takes src as it stands")
        void readsSrc() {
            assertEquals("https://cdn.example.com/photo.jpg",
                    resolved("<img src='https://cdn.example.com/photo.jpg'>"));
        }

        @Test
        @DisplayName("resolves a relative src against the page it came from")
        void resolvesRelativeSrc() {
            assertEquals("https://example.com/article/photo.jpg", resolved("<img src='photo.jpg'>"));
        }

        @Test
        @DisplayName("resolves a root-relative src too")
        void resolvesRootRelativeSrc() {
            assertEquals("https://example.com/img/photo.png", resolved("<img src='/img/photo.png'>"));
        }
    }

    @Nested
    @DisplayName("when the page defers its images")
    class Lazy {

        /**
         * The common shape: a one-pixel transparent GIF holding the layout open while
         * the real URL waits in a data- attribute for JavaScript that Harbor never
         * runs.
         */
        @Test
        @DisplayName("ignores a placeholder src in favour of data-src")
        void prefersDataSrcOverAPlaceholder() {
            assertEquals("https://cdn.example.com/real.jpg", resolved(
                    "<img src='data:image/gif;base64,R0lGODlhAQABAAAAACw=' "
                            + "data-src='https://cdn.example.com/real.jpg'>"));
        }

        @Test
        @DisplayName("reads the other data- attributes publishers use")
        void readsTheOtherLazyAttributes() {
            assertEquals("https://cdn.example.com/a.jpg",
                    resolved("<img data-original='https://cdn.example.com/a.jpg'>"));
            assertEquals("https://cdn.example.com/b.jpg",
                    resolved("<img data-lazy-src='https://cdn.example.com/b.jpg'>"));
        }

        /**
         * No {@code data:} URI is a candidate, whatever its size: this reports what to
         * fetch, and those bytes are already here. Size says nothing about
         * fetchability — it only bears on whether the bytes are a picture or a
         * spacer, which is the renderer's question.
         */
        @Test
        @DisplayName("offers no candidate for inline bytes, spacer or picture")
        void offersNothingForInlineBytes() {
            String spacer = "<img src='data:image/gif;base64,R0lGODlhAQABAAAAACw='>";
            String picture = "<img src='data:image/png;base64," + "A".repeat(4000) + "'>";

            assertTrue(ArticleImageSource.bestFor(image(spacer)).isEmpty());
            assertTrue(ArticleImageSource.bestFor(image(picture)).isEmpty());
        }
    }

    @Nested
    @DisplayName("when the page offers several sizes")
    class Srcset {

        @Test
        @DisplayName("takes the widest candidate rather than the first")
        void takesTheWidest() {
            assertEquals("https://cdn.example.com/large.jpg", resolved(
                    "<img srcset='https://cdn.example.com/small.jpg 480w, "
                            + "https://cdn.example.com/large.jpg 1200w, "
                            + "https://cdn.example.com/medium.jpg 800w'>"));
        }

        @Test
        @DisplayName("still finds one when the descriptors are missing")
        void copesWithoutDescriptors() {
            assertEquals("https://cdn.example.com/only.jpg",
                    resolved("<img srcset='https://cdn.example.com/only.jpg'>"));
        }

        @Test
        @DisplayName("reads a deferred srcset as well")
        void readsDataSrcset() {
            assertEquals("https://cdn.example.com/wide.jpg", resolved(
                    "<img src='data:image/gif;base64,R0lGODlh' "
                            + "data-srcset='https://cdn.example.com/narrow.jpg 400w, "
                            + "https://cdn.example.com/wide.jpg 1600w'>"));
        }
    }

    @Nested
    @DisplayName("when the page lists formats by preference")
    class Picture {

        /**
         * Publishers put AVIF and WebP first because browsers prefer them. PDFBox can
         * embed neither, so the fallback is the only usable source — taking the first
         * would archive nothing.
         */
        @Test
        @DisplayName("skips the formats PDFBox cannot embed and takes the one it can")
        void skipsUndecodableFormats() {
            assertEquals("https://cdn.example.com/photo.jpg", resolved("""
                    <picture>
                      <source type="image/avif" srcset="https://cdn.example.com/photo.avif">
                      <source type="image/webp" srcset="https://cdn.example.com/photo.webp">
                      <source type="image/jpeg" srcset="https://cdn.example.com/photo.jpg">
                      <img src="https://cdn.example.com/photo.jpg">
                    </picture>"""));
        }

        @Test
        @DisplayName("falls back to the img when no source is usable")
        void fallsBackToTheImg() {
            assertEquals("https://cdn.example.com/photo.png", resolved("""
                    <picture>
                      <source type="image/avif" srcset="https://cdn.example.com/photo.avif">
                      <img src="https://cdn.example.com/photo.png">
                    </picture>"""));
        }
    }

    @Nested
    @DisplayName("when there is nothing usable")
    class Nothing {

        @Test
        @DisplayName("offers no candidate for an img with no source at all")
        void findsNothingWithoutASource() {
            assertTrue(ArticleImageSource.bestFor(image("<img alt='described but absent'>")).isEmpty());
        }

        /**
         * Fetching one only to find PDFBox cannot read it wastes a request against a
         * stranger's server, so the format is judged before the fetch.
         */
        @Test
        @DisplayName("refuses a format that cannot be embedded")
        void refusesUndecodableFormats() {
            assertTrue(ArticleImageSource.bestFor(
                    image("<img src='https://cdn.example.com/photo.avif'>")).isEmpty());
            assertTrue(ArticleImageSource.bestFor(
                    image("<img src='https://cdn.example.com/photo.webp'>")).isEmpty());
        }

        @Test
        @DisplayName("refuses a scheme that is not the web's")
        void refusesOtherSchemes() {
            assertTrue(ArticleImageSource.bestFor(
                    image("<img src='file:///etc/passwd.png'>")).isEmpty());
        }

        @Test
        @DisplayName("keeps a query string, which CDNs use to size an image")
        void keepsQueryStrings() {
            assertEquals("https://cdn.example.com/photo.jpg?w=1200",
                    resolved("<img src='https://cdn.example.com/photo.jpg?w=1200'>"));
        }
    }

    private static String resolved(String html) {
        return ArticleImageSource.bestFor(image(html)).orElseThrow(
                () -> new AssertionError("No image source was found in: " + html));
    }

    private static Element image(String html) {
        return Jsoup.parse(html, PAGE).selectFirst("img");
    }
}
