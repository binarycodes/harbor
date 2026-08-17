package io.binarycodes.harbor.library.domain;

import java.util.Locale;

/**
 * What kind of thing a saved link points at. Drives the type label on a card and
 * nothing else, so an unrecognised page falls back to {@link #ARTICLE}.
 */
public enum BookmarkType {

    ARTICLE,
    PAPER,
    VIDEO,
    REPOSITORY,
    GUIDE,
    BOOK,
    ESSAY;

    public String translationKey() {
        return "bookmark.type." + name().toLowerCase(Locale.ROOT);
    }
}
