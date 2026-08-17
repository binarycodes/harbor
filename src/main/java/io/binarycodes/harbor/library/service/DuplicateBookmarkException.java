package io.binarycodes.harbor.library.service;

import io.binarycodes.harbor.library.domain.Bookmark;

/**
 * Raised when a link that is already in the library is saved again. It carries the
 * bookmark that already holds that URL, so the dialog can point the reader at the
 * entry to edit rather than leaving them with two copies of one page.
 */
public class DuplicateBookmarkException extends RuntimeException {

    private final transient Bookmark existing;

    public DuplicateBookmarkException(Bookmark existing) {
        super("%s is already saved as %s".formatted(existing.url(), existing.id()));
        this.existing = existing;
    }

    public Bookmark getExisting() {
        return existing;
    }
}
