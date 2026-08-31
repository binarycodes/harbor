package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Completing a typed URL")
class AbsoluteUrlTest {

    @Nested
    @DisplayName("the scheme")
    class Scheme {

        @Test
        @DisplayName("is assumed to be https when it was left out")
        void assumesHttps() {
            assertEquals("https://example.com/thing", AbsoluteUrl.of("example.com/thing"));
        }

        @Test
        @DisplayName("is assumed for a bare host too, which is what most readers type")
        void assumesHttpsForABareHost() {
            assertEquals("https://vaadin.com", AbsoluteUrl.of("vaadin.com"));
        }

        @Test
        @DisplayName("is left alone when there already is one")
        void keepsExistingScheme() {
            assertEquals("http://example.com/thing", AbsoluteUrl.of("http://example.com/thing"));
        }

        /**
         * Surrounding space is what a paste brings with it, and it must not be mistaken
         * for a URL that cannot be parsed.
         */
        @Test
        @DisplayName("survives the whitespace a paste brings with it")
        void stripsSurroundingSpace() {
            assertEquals("https://vaadin.com", AbsoluteUrl.of("  vaadin.com  "));
        }
    }

    @Nested
    @DisplayName("anything the server has no business fetching")
    class Refused {

        @ParameterizedTest
        @ValueSource(strings = {
                "file:///etc/passwd",
                "jar:file:///tmp/x.jar!/y",
                "https://",
                "https://exa mple.com" })
        @DisplayName("is refused rather than completed")
        void refusesIt(String hostile) {
            assertThrows(IllegalArgumentException.class, () -> AbsoluteUrl.of(hostile));
        }

        @ParameterizedTest
        @ValueSource(strings = { "file:///etc/passwd", "https://", "https://exa mple.com" })
        @DisplayName("comes back untouched from the lenient form, never quietly completed")
        void leavesItAloneLeniently(String hostile) {
            assertEquals(hostile, AbsoluteUrl.ofOrSame(hostile));
        }
    }

    /**
     * The lenient form is for a field the reader is halfway through typing. It must
     * complete what it can and hand back the rest unchanged, so that a URL too broken
     * to parse fails where failures are reported rather than here.
     */
    @Nested
    @DisplayName("the lenient form")
    class Lenient {

        @Test
        @DisplayName("completes a URL it understands")
        void completesWhatItCan() {
            assertEquals("https://vaadin.com", AbsoluteUrl.ofOrSame("vaadin.com"));
        }

        @Test
        @DisplayName("hands back a half-typed one unchanged")
        void keepsWhatItCannot() {
            assertEquals("", AbsoluteUrl.ofOrSame(""));
        }
    }
}
