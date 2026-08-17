package io.binarycodes.harbor.library.domain;

/**
 * A tag and how many bookmarks carry it, for the sidebar's filter list.
 */
public record TagCount(String name, int count) {

    public int colorIndex() {
        return PaletteIndex.forText(name);
    }
}
