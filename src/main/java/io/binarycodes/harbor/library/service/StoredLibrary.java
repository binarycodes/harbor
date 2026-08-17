package io.binarycodes.harbor.library.service;

import java.util.List;

import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.ColorSchemePreference;

/**
 * The whole persisted payload: the bookmarks and the light/dark choice, written
 * and read as one document.
 */
public record StoredLibrary(List<Bookmark> bookmarks, ColorSchemePreference colorScheme) {

    public StoredLibrary {
        bookmarks = bookmarks == null ? List.of() : List.copyOf(bookmarks);
        colorScheme = colorScheme == null ? ColorSchemePreference.SYSTEM : colorScheme;
    }

    public static StoredLibrary empty() {
        return new StoredLibrary(List.of(), ColorSchemePreference.SYSTEM);
    }
}
