package io.binarycodes.harbor.library.domain;

import java.util.Locale;

/**
 * How densely a library listing is rendered: picture cards, wide rows, or a
 * table.
 */
public enum ViewMode {

    CARDS,
    ROWS,
    COMPACT;

    public String translationKey() {
        return "library.mode." + name().toLowerCase(Locale.ROOT);
    }
}
