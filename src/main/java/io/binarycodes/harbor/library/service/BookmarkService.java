package io.binarycodes.harbor.library.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.Highlight;
import io.binarycodes.harbor.library.domain.LibraryQuery;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.domain.LinkDraft;
import io.binarycodes.harbor.library.domain.SortMode;
import io.binarycodes.harbor.library.domain.TagCount;
import tools.jackson.databind.json.JsonMapper;

/**
 * The library: everything saved, and every change to it. Every question is a
 * query, which is what lets a library grow past what one session could hold in
 * memory — an article body is measured in tens of kilobytes and there is no
 * ceiling on how many there are.
 *
 * <p>Nothing here knows a screen exists. Entities and the repository stay inside
 * this package, so what leaves is always an immutable record, and always inside
 * the transaction that produced it.
 */
@Component
public class BookmarkService {

    private final BookmarkRepository repository;
    private final LibraryOwner owner;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    BookmarkService(BookmarkRepository repository, LibraryOwner owner, Clock clock) {
        this.repository = repository;
        this.owner = owner;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Bookmark> find(LibraryQuery query) {
        return BookmarkMapper.toBookmarks(repository.findMatching(
                owner.current(),
                query.scope() == LibraryScope.READ_LATER,
                jsonMapper.writeValueAsString(query.tags()),
                query.searchText().toLowerCase(Locale.ROOT),
                sortFor(query.sortMode())));
    }

    @Transactional(readOnly = true)
    public Optional<Bookmark> findById(String id) {
        UUID key = BookmarkMapper.idOf(id);
        return key == null
                ? Optional.empty()
                : repository.findByOwnerIdAndId(owner.current(), key).map(BookmarkMapper::toBookmark);
    }

    @Transactional(readOnly = true)
    public List<Bookmark> withHighlights() {
        return BookmarkMapper.toBookmarks(repository.findAnnotated(owner.current()));
    }

    @Transactional(readOnly = true)
    public int count() {
        return (int) repository.countByOwnerId(owner.current());
    }

    @Transactional(readOnly = true)
    public int countReadLater() {
        return (int) repository.countByOwnerIdAndReadLaterTrue(owner.current());
    }

    @Transactional(readOnly = true)
    public int countHighlights() {
        return (int) repository.countHighlights(owner.current());
    }

    @Transactional(readOnly = true)
    public List<TagCount> tagCounts() {
        return repository.tagCounts(owner.current()).stream()
                .map(row -> new TagCount(row.getName(), row.getCount()))
                .toList();
    }

    /**
     * The same page saved twice is a mistake worth refusing rather than silently
     * keeping both, since the second copy carries none of the notes or highlights
     * the reader left on the first.
     */
    @Transactional(readOnly = true)
    public Optional<Bookmark> findByUrl(String url) {
        return repository.findByOwnerIdAndUrlKey(owner.current(), UrlKey.of(url))
                .map(BookmarkMapper::toBookmark);
    }

    @Transactional
    public Bookmark add(LinkDraft draft) {
        refuseDuplicate(draft.getUrl(), null);
        BookmarkEntity entity = new BookmarkEntity();
        // The id is the database's to assign, so the record handed to the mapper
        // has none yet; what comes back from the save is the one that counts.
        BookmarkMapper.apply(new Bookmark(
                null,
                draft.getUrl(),
                draft.getTitle(),
                draft.getSite(),
                draft.getSite(),
                draft.getDescription(),
                draft.tagsOrEmpty(),
                draft.getType(),
                draft.isReadLater(),
                clock.millis(),
                Math.max(1, draft.getReadingMinutes()),
                draft.getContent(),
                "",
                List.of()), entity, owner.current());
        return BookmarkMapper.toBookmark(save(entity));
    }

    /**
     * Writes the dialog's fields back over an existing bookmark. What the reader
     * added themselves — the notes, the highlights, and when they saved it — is
     * carried across untouched: editing a title is not a reason to lose any of it.
     */
    @Transactional
    public void update(String id, LinkDraft draft) {
        refuseDuplicate(draft.getUrl(), id);
        replace(id, existing -> new Bookmark(
                existing.id(),
                draft.getUrl(),
                draft.getTitle(),
                draft.getSite(),
                draft.getSite(),
                draft.getDescription(),
                draft.tagsOrEmpty(),
                draft.getType(),
                draft.isReadLater(),
                existing.savedAt(),
                Math.max(1, draft.getReadingMinutes()),
                draft.getContent(),
                existing.notes(),
                existing.highlights()));
    }

    @Transactional
    public void toggleReadLater(String id) {
        replace(id, bookmark -> bookmark.withReadLater(!bookmark.readLater()));
    }

    @Transactional
    public void updateNotes(String id, String notes) {
        replace(id, bookmark -> bookmark.withNotes(notes));
    }

    @Transactional
    public void addHighlight(String id, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        replace(id, bookmark -> {
            List<Highlight> highlights = new ArrayList<>(bookmark.highlights());
            highlights.add(new Highlight(text));
            return bookmark.withHighlights(highlights);
        });
    }

    @Transactional
    public void removeHighlight(String id, int index) {
        replace(id, bookmark -> {
            if (index < 0 || index >= bookmark.highlights().size()) {
                return bookmark;
            }
            List<Highlight> highlights = new ArrayList<>(bookmark.highlights());
            highlights.remove(index);
            return bookmark.withHighlights(highlights);
        });
    }

    @Transactional
    public void remove(String id) {
        UUID key = BookmarkMapper.idOf(id);
        if (key != null) {
            repository.findByOwnerIdAndId(owner.current(), key).ifPresent(repository::delete);
        }
    }

    /**
     * Takes in bookmarks saved before there was a database, keeping whatever the
     * reader had already written on them. Anything whose URL is here already is
     * left alone rather than refused: an import that fails halfway because one
     * link was re-saved is worse than one that skips it.
     *
     * @return how many were actually taken in
     */
    @Transactional
    public int importAll(List<Bookmark> bookmarks) {
        int imported = 0;
        for (Bookmark bookmark : bookmarks) {
            if (repository.findByOwnerIdAndUrlKey(owner.current(), UrlKey.of(bookmark.url())).isPresent()) {
                continue;
            }
            BookmarkEntity entity = new BookmarkEntity();
            BookmarkMapper.apply(bookmark, entity, owner.current());
            save(entity);
            imported++;
        }
        return imported;
    }

    private void refuseDuplicate(String url, String allowedId) {
        repository.findByOwnerIdAndUrlKey(owner.current(), UrlKey.of(url))
                .filter(other -> !other.getId().toString().equals(allowedId))
                .ifPresent(other -> {
                    throw new DuplicateBookmarkException(BookmarkMapper.toBookmark(other));
                });
    }

    private void replace(String id, UnaryOperator<Bookmark> change) {
        UUID key = BookmarkMapper.idOf(id);
        if (key == null) {
            return;
        }
        repository.findByOwnerIdAndId(owner.current(), key).ifPresent(entity -> {
            BookmarkMapper.apply(change.apply(BookmarkMapper.toBookmark(entity)), entity, owner.current());
            save(entity);
        });
    }

    /**
     * Flushed rather than left to the commit, so that the unique index on the
     * normalised URL reports a clash here — where it can still be turned into the
     * same refusal the pre-check gives — rather than after this method has
     * returned. The pre-check is the friendly path; this is the one that holds
     * when two sessions save the same link at once.
     */
    private BookmarkEntity save(BookmarkEntity entity) {
        try {
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException clash) {
            throw repository.findByOwnerIdAndUrlKey(owner.current(), entity.getUrlKey())
                    .map(BookmarkMapper::toBookmark)
                    .map(DuplicateBookmarkException::new)
                    .orElseThrow(() -> clash);
        }
    }

    /**
     * Bare column names, with no table alias: the ordering is applied outside the
     * query it belongs to, where the alias is no longer in scope.
     */
    private static Sort sortFor(SortMode sortMode) {
        return switch (sortMode) {
            case RECENT -> JpaSort.unsafe(Sort.Direction.DESC, "saved_at");
            case TITLE -> JpaSort.unsafe(Sort.Direction.ASC, "title collate \"und-x-icu\"");
            case READING_TIME_SHORTEST -> JpaSort.unsafe(Sort.Direction.ASC, "reading_minutes");
            case READING_TIME_LONGEST -> JpaSort.unsafe(Sort.Direction.DESC, "reading_minutes");
        };
    }
}
