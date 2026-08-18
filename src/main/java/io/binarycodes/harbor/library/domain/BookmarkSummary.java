package io.binarycodes.harbor.library.domain;

import java.util.List;

/**
 * A bookmark as a listing shows it: everything a card, a row or a table cell
 * renders, and nothing else.
 *
 * <p>What it leaves out is the article itself. A listing draws the whole library
 * at once, and the body is by far the largest thing on a bookmark — reading every
 * one of them to draw a grid of titles is the cost this type exists to avoid. The
 * reader is the only screen that needs the article, and it asks for one bookmark
 * at a time.
 *
 * <p>Highlights and notes are here as a count and a flag for the same reason: the
 * listing shows that there are some, never what they say.
 */
public record BookmarkSummary(
        String id,
        String title,
        String site,
        String description,
        List<String> tags,
        BookmarkType type,
        boolean readLater,
        long savedAt,
        int readingMinutes,
        int highlightCount,
        boolean hasNotes,
        boolean hasArchive) {

    public BookmarkSummary {
        tags = tags == null ? List.of() : List.copyOf(tags);
        type = type == null ? BookmarkType.ARTICLE : type;
    }

    public int coverIndex() {
        return PaletteIndex.forText(site);
    }

    public boolean hasHighlights() {
        return highlightCount > 0;
    }
}
