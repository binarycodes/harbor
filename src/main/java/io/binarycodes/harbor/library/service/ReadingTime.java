package io.binarycodes.harbor.library.service;

/**
 * How long an article takes to read, at the 200 words per minute that reading
 * research puts adult prose at. Anything with text at all is reported as at least
 * a minute, because "0 min" reads as an error rather than as an estimate.
 */
public final class ReadingTime {

    private static final int WORDS_PER_MINUTE = 200;

    private ReadingTime() {
    }

    public static int minutes(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int words = text.strip().split("\\s+").length;
        return Math.max(1, Math.round((float) words / WORDS_PER_MINUTE));
    }
}
