package io.binarycodes.harbor.library.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.binarycodes.harbor.library.domain.ArchiveStatus;
import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.BookmarkSummary;
import io.binarycodes.harbor.library.domain.BookmarkType;
import io.binarycodes.harbor.library.domain.Highlight;
import io.binarycodes.harbor.library.domain.HighlightGroup;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Between the row and the record. The only place either shape knows the other
 * exists.
 */
final class BookmarkMapper {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> TAGS = new TypeReference<>() {
    };
    private static final TypeReference<List<Highlight>> HIGHLIGHTS = new TypeReference<>() {
    };

    private BookmarkMapper() {
    }

    static Bookmark toBookmark(BookmarkEntity entity) {
        return new Bookmark(
                entity.getId().toString(),
                entity.getUrl(),
                entity.getTitle(),
                entity.getSite(),
                entity.getAuthor(),
                entity.getDescription(),
                entity.getTags(),
                entity.getType(),
                entity.isReadLater(),
                entity.getSavedAt(),
                entity.getReadingMinutes(),
                entity.getContent(),
                entity.getNotes(),
                entity.getHighlights(),
                entity.getArchiveStatus());
    }

    static List<Bookmark> toBookmarks(List<BookmarkEntity> entities) {
        return entities.stream().map(BookmarkMapper::toBookmark).toList();
    }

    static List<BookmarkSummary> toSummaries(List<BookmarkSummaryRow> rows) {
        return rows.stream()
                .map(row -> new BookmarkSummary(
                        row.getId(),
                        row.getTitle(),
                        row.getSite(),
                        row.getDescription(),
                        JSON.readValue(row.getTags(), TAGS),
                        BookmarkType.valueOf(row.getType()),
                        row.getReadLater(),
                        row.getSavedAt(),
                        row.getReadingMinutes(),
                        row.getHighlightCount(),
                        row.getHasNotes(),
                        row.getHasArchive()))
                .toList();
    }

    static List<HighlightGroup> toHighlightGroups(List<HighlightGroupRow> rows) {
        return rows.stream()
                .map(row -> new HighlightGroup(
                        row.getId(),
                        row.getTitle(),
                        row.getSite(),
                        JSON.readValue(row.getHighlights(), HIGHLIGHTS)))
                .toList();
    }

    /**
     * Writes a bookmark over a row, leaving the id and the version alone — those
     * belong to whoever loaded it.
     */
    static void apply(Bookmark bookmark, BookmarkEntity entity, String ownerId) {
        entity.setOwnerId(ownerId);
        entity.setUrl(bookmark.url());
        entity.setUrlKey(UrlKey.of(bookmark.url()));
        entity.setTitle(bookmark.title());
        entity.setSite(bookmark.site());
        entity.setAuthor(bookmark.author());
        entity.setDescription(bookmark.description());
        entity.setTags(bookmark.tags());
        entity.setType(bookmark.type());
        entity.setReadLater(bookmark.readLater());
        entity.setSavedAt(bookmark.savedAt());
        entity.setReadingMinutes(bookmark.readingMinutes());
        entity.setContent(bookmark.content());
        entity.setNotes(bookmark.notes());
        entity.setHighlights(bookmark.highlights());
        entity.setArchiveStatus(bookmark.archiveStatus());
        // Lower-cased here rather than at query time so the trigram index is the
        // one the search actually uses.
        entity.setSearchText(bookmark.searchableText().toLowerCase(Locale.ROOT));
    }

    /**
     * A route parameter is whatever was typed into the address bar, so it is not
     * necessarily a UUID at all.
     */
    static UUID idOf(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }
}
