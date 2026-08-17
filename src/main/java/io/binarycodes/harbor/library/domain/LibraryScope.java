package io.binarycodes.harbor.library.domain;

import java.util.Locale;

/**
 * Which slice of the library a listing covers.
 */
public enum LibraryScope {

    ALL,
    READ_LATER;

    public String titleKey() {
        return "library.scope." + name().toLowerCase(Locale.ROOT) + ".title";
    }
}
