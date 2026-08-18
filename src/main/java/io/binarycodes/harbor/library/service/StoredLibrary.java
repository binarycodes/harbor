package io.binarycodes.harbor.library.service;

import java.util.List;

import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.ColorSchemePreference;

/**
 * What older versions of Harbor kept in the browser: the whole library and the
 * light/dark choice, as one document. Nothing writes this shape any more — it
 * exists so {@link LegacyLibraryDecoder} can read what is already out there.
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
