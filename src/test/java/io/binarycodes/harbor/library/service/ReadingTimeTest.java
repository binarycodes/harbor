package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reading time")
class ReadingTimeTest {

    @Test
    @DisplayName("is nothing at all when there is no text")
    void isZeroWithoutText() {
        assertEquals(0, ReadingTime.minutes(null));
        assertEquals(0, ReadingTime.minutes(""));
        assertEquals(0, ReadingTime.minutes("   \n  "));
    }

    @Test
    @DisplayName("rounds up to a minute for anything shorter")
    void roundsShortTextUpToOneMinute() {
        assertEquals(1, ReadingTime.minutes("Three short words"));
    }

    @Test
    @DisplayName("counts 200 words to the minute")
    void countsTwoHundredWordsPerMinute() {
        assertEquals(3, ReadingTime.minutes(words(600)));
        assertEquals(10, ReadingTime.minutes(words(2000)));
    }

    @Test
    @DisplayName("is not thrown off by runs of whitespace")
    void ignoresExtraWhitespace() {
        assertEquals(1, ReadingTime.minutes("  one\n\n  two \t three  "));
    }

    private static String words(int count) {
        return "word ".repeat(count).strip();
    }
}
