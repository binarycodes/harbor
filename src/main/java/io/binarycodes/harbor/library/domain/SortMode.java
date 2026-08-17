package io.binarycodes.harbor.library.domain;

import java.util.Locale;

/**
 * The orders a library listing can be shown in.
 */
public enum SortMode {

    RECENT,
    TITLE,
    READING_TIME_SHORTEST,
    READING_TIME_LONGEST;

    public String translationKey() {
        return "library.sort." + name().toLowerCase(Locale.ROOT);
    }
}
