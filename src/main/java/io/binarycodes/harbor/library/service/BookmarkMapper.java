package io.binarycodes.harbor.library.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.binarycodes.harbor.library.domain.Bookmark;

/**
 * Between the row and the record. The only place either shape knows the other
 * exists.
 */
final class BookmarkMapper {

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
                entity.getHighlights());
    }

    static List<Bookmark> toBookmarks(List<BookmarkEntity> entities) {
        return entities.stream().map(BookmarkMapper::toBookmark).toList();
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
