package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("How the archiver is configured")
class ArchivePropertiesTest {

    @Nested
    @DisplayName("the browser it renders in")
    class Browser {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "  " })
        @DisplayName("must be named, since Harbor cannot archive without one")
        void refusesAnAbsentBrowser(String absent) {
            assertThrows(IllegalStateException.class,
                    () -> new ArchiveProperties(absent, null, 0, true));
        }

        @Test
        @DisplayName("comes with a timeout and a viewport when neither was set")
        void defaultsTheRest() {
            ArchiveProperties properties = new ArchiveProperties("http://chromium:9222", null, 0, true);

            assertEquals(Duration.ofSeconds(30), properties.browserTimeout());
            assertEquals(1280, properties.viewportWidth());
        }
    }

    @Nested
    @DisplayName("whether the reader waits for the archive")
    class WaitingForTheArchive {

        /**
         * The one default that is a promise rather than a convenience: with it absent, a
         * deployment that meant to keep the guarantee still has it. Turning it off has to
         * be something somebody wrote down.
         */
        @Test
        @DisplayName("is on when nothing said otherwise")
        void holdsTheSaveOpenByDefault() {
            assertTrue(new ArchiveProperties("http://chromium:9222", null, 0, null).forceBeforeSave());
        }

        @Test
        @DisplayName("is off only when it was actually set off")
        void respectsAConfiguredFalse() {
            assertFalse(new ArchiveProperties("http://chromium:9222", null, 0, false).forceBeforeSave());
        }
    }
}
