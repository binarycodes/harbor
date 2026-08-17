package io.binarycodes.harbor.library.domain;

/**
 * Picks one of the ten cover colors for a piece of text. The same site or tag
 * name always lands on the same color, so a bookmark's monogram tile and a tag's
 * dot stay recognisable between sessions without either having to store a color.
 */
public final class PaletteIndex {

    public static final int COLOR_COUNT = 10;

    private static final int HASH_MULTIPLIER = 31;

    private PaletteIndex() {
    }

    public static int forText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int hash = 0;
        for (int position = 0; position < text.length(); position++) {
            hash = hash * HASH_MULTIPLIER + text.charAt(position);
        }
        return Math.floorMod(hash, COLOR_COUNT);
    }
}
