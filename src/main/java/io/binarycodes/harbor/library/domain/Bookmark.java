package io.binarycodes.harbor.library.domain;

import java.util.List;

/**
 * One saved link with everything the reader has added to it. Immutable: the
 * {@code with…} methods return a fresh copy, which keeps a bookmark safe to hand
 * to a grid or a card while the library is being edited elsewhere.
 */
public record Bookmark(
        String id,
        String url,
        String title,
        String site,
        String author,
        String description,
        List<String> tags,
        BookmarkType type,
        boolean readLater,
        long savedAt,
        int readingMinutes,
        String content,
        String notes,
        List<Highlight> highlights) {

    public Bookmark {
        tags = tags == null ? List.of() : List.copyOf(tags);
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        type = type == null ? BookmarkType.ARTICLE : type;
        notes = notes == null ? "" : notes;
        content = content == null ? "" : content;
    }

    public Bookmark withReadLater(boolean value) {
        return new Bookmark(id, url, title, site, author, description, tags, type, value, savedAt,
                readingMinutes, content, notes, highlights);
    }

    public Bookmark withNotes(String value) {
        return new Bookmark(id, url, title, site, author, description, tags, type, readLater, savedAt,
                readingMinutes, content, value, highlights);
    }

    public Bookmark withHighlights(List<Highlight> value) {
        return new Bookmark(id, url, title, site, author, description, tags, type, readLater, savedAt,
                readingMinutes, content, notes, value);
    }

    public int coverIndex() {
        return PaletteIndex.forText(site);
    }

    public boolean hasNotes() {
        return !notes.isBlank();
    }

    public boolean hasHighlights() {
        return !highlights.isEmpty();
    }

    /**
     * Everything a search should look through — the visible fields plus the
     * article body, the reader's notes, and the passages they kept.
     */
    public String searchableText() {
        return String.join(" ",
                title == null ? "" : title,
                description == null ? "" : description,
                site == null ? "" : site,
                String.join(" ", tags),
                notes,
                content,
                highlights.stream().map(Highlight::text).reduce("", (left, right) -> left + " " + right));
    }
}
