package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.binarycodes.harbor.library.domain.BookmarkType;

@DisplayName("Describing a link from its URL alone")
class UrlHeuristicMetadataTest {

    private final UrlHeuristicMetadata heuristic = new UrlHeuristicMetadata();

    @Nested
    @DisplayName("the site")
    class Site {

        @Test
        @DisplayName("drops the www prefix")
        void dropsWww() {
            assertEquals("example.com", heuristic.resolve("https://www.example.com/thing").site());
        }

        @Test
        @DisplayName("is found even when the scheme was left out")
        void assumesHttps() {
            assertEquals("example.com", heuristic.resolve("example.com/thing").site());
        }

        @Test
        @DisplayName("falls back to a placeholder for something that is not a URL")
        void handlesNonsense() {
            assertEquals("link", heuristic.resolve("not a url at all").site());
            assertEquals("link", heuristic.resolve(null).site());
        }
    }

    @Nested
    @DisplayName("the title")
    class Title {

        @Test
        @DisplayName("comes from the last readable path segment")
        void usesLastReadableSegment() {
            assertEquals("Interactive Guide To Flexbox",
                    heuristic.resolve("https://joshwcomeau.com/css/interactive-guide-to-flexbox/").title());
        }

        @Test
        @DisplayName("skips identifiers and dates on the way")
        void skipsIdentifiers() {
            assertEquals("Great Work",
                    heuristic.resolve("https://example.com/great-work/2026/11/8831").title());
        }

        @Test
        @DisplayName("loses the file extension")
        void stripsFileExtensions() {
            assertEquals("How To Do Great Work",
                    heuristic.resolve("https://paulgraham.com/how-to-do-great-work.html").title());
        }

        @Test
        @DisplayName("falls back to the site name when the path says nothing")
        void fallsBackToSiteName() {
            assertEquals("Arxiv", heuristic.resolve("https://arxiv.org/abs/1706.03762").title());
            assertEquals("Example", heuristic.resolve("https://www.example.com/").title());
        }
    }

    @Nested
    @DisplayName("the kind of thing it is")
    class Kind {

        @Test
        @DisplayName("is recognised for the hosts worth recognising")
        void recognisesKnownHosts() {
            assertEquals(BookmarkType.PAPER, heuristic.resolve("https://arxiv.org/abs/1706.03762").type());
            assertEquals(BookmarkType.REPOSITORY,
                    heuristic.resolve("https://github.com/sindresorhus/awesome").type());
            assertEquals(BookmarkType.VIDEO,
                    heuristic.resolve("https://www.youtube.com/watch?v=x7drE24geUw").type());
            assertEquals(BookmarkType.VIDEO, heuristic.resolve("https://youtu.be/x7drE24geUw").type());
            assertEquals(BookmarkType.PAPER, heuristic.resolve("https://www.nature.com/articles/x").type());
            assertEquals(BookmarkType.GUIDE, heuristic.resolve("https://en.wikipedia.org/wiki/Harbor").type());
            assertEquals(BookmarkType.ARTICLE, heuristic.resolve("https://blog.example.com/post").type());
        }

        @Test
        @DisplayName("defaults to an article for anything else")
        void defaultsToArticle() {
            LinkMetadata metadata = heuristic.resolve("https://some-personal-site.dev/writing/thing");

            assertEquals(BookmarkType.ARTICLE, metadata.type());
            assertEquals(List.of("Reading"), metadata.tags());
        }

        @Test
        @DisplayName("suggests tags that fit the host")
        void suggestsTags() {
            assertEquals(List.of("Research", "AI"), heuristic.resolve("https://arxiv.org/abs/1").tags());
            assertEquals(List.of("Reference"),
                    heuristic.resolve("https://en.wikipedia.org/wiki/Harbor").tags());
        }
    }

    @Test
    @DisplayName("invents no description and no article text")
    void inventsNothingElse() {
        LinkMetadata metadata = heuristic.resolve("https://example.com/thing");

        assertTrue(metadata.description().isEmpty());
        assertTrue(metadata.content().isEmpty());
        assertEquals(0, metadata.readingMinutes());
    }
}
