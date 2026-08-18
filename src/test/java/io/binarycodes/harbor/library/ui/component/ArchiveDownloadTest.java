package io.binarycodes.harbor.library.ui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Naming the downloaded archive")
class ArchiveDownloadTest {

    @Test
    @DisplayName("turns a title into something a filesystem accepts")
    void makesATitleSafe() {
        assertEquals("an-interactive-guide-to-flexbox.pdf",
                ArchiveDownload.filenameFor("An Interactive Guide to Flexbox"));
    }

    /**
     * A title is prose: it can hold slashes, quotes and colons, none of which belong
     * in a filename or in a Content-Disposition header.
     */
    @Test
    @DisplayName("removes what a filename cannot carry")
    void removesAwkwardCharacters() {
        assertEquals("notes-on-c-and-or-rust.pdf",
                ArchiveDownload.filenameFor("Notes on C++ / and \"or\" Rust:"));
        assertEquals("path-traversal.pdf", ArchiveDownload.filenameFor("../../path/traversal"));
    }

    @Test
    @DisplayName("falls back to a name when the title leaves nothing behind")
    void fallsBackForAnEmptyTitle() {
        assertEquals("archive.pdf", ArchiveDownload.filenameFor(""));
        assertEquals("archive.pdf", ArchiveDownload.filenameFor("！！！"));
        assertEquals("archive.pdf", ArchiveDownload.filenameFor(null));
    }

    @Test
    @DisplayName("shortens a very long title without leaving a trailing hyphen")
    void shortensALongTitle() {
        String filename = ArchiveDownload.filenameFor("word ".repeat(60));

        assertTrue(filename.length() <= 64, "was " + filename.length());
        assertTrue(filename.endsWith(".pdf"));
        assertFalse(filename.contains("-.pdf"), "a trailing hyphen would look like a truncation bug");
    }
}
