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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.binarycodes.harbor.library.domain.ArchiveStatus;
import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.BookmarkSummary;
import io.binarycodes.harbor.library.domain.Highlight;
import io.binarycodes.harbor.library.domain.HighlightGroup;
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
 *
 * <p>Every bookmark carries an archive of its page, or is on its way to one.
 * {@code harbor.archive.force-before-save} decides which: with it on, {@link #add}
 * refuses a draft with no archive, because the save dialog is only one caller and an
 * invariant guarded at the screen is an invariant with a way around it. With it off,
 * the same draft is filed {@link ArchiveStatus#PENDING} and handed to
 * {@link BackgroundArchiver} — the bookmark is saved at once, and its copy of the
 * page follows.
 */
@Component
public class BookmarkService {

    private final BookmarkRepository repository;
    private final BookmarkArchiveService archives;
    private final BackgroundArchiver backgroundArchiver;
    private final ArchiveProperties archiveProperties;
    private final LibraryOwner owner;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    BookmarkService(BookmarkRepository repository, BookmarkArchiveService archives,
            BackgroundArchiver backgroundArchiver, ArchiveProperties archiveProperties,
            LibraryOwner owner, Clock clock) {
        this.repository = repository;
        this.archives = archives;
        this.backgroundArchiver = backgroundArchiver;
        this.archiveProperties = archiveProperties;
        this.owner = owner;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BookmarkSummary> find(LibraryQuery query) {
        return BookmarkMapper.toSummaries(repository.findMatching(
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
    public List<HighlightGroup> withHighlights() {
        return BookmarkMapper.toHighlightGroups(repository.findAnnotated(owner.current()));
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
        boolean archived = carriesArchive(draft);
        if (!archived && archiveProperties.forceBeforeSave()) {
            throw new IllegalArgumentException(
                    "A bookmark cannot be saved without an archive of its page");
        }
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
                List.of(),
                archived ? ArchiveStatus.READY : ArchiveStatus.PENDING), entity, owner.current());
        Bookmark saved = BookmarkMapper.toBookmark(save(entity));
        keepArchive(saved.id(), draft);
        if (!archived) {
            archiveAfterCommit(new ArchiveRequest(saved.id(), owner.current(), saved.url(),
                    saved.title()));
        }
        return saved;
    }

    /**
     * Writes the dialog's fields back over an existing bookmark. What the reader
     * added themselves — the notes, the highlights, and when they saved it — is
     * carried across untouched: editing a title is not a reason to lose any of it.
     *
     * <p>This one is an overwrite, so a version conflict is not something to
     * quietly retry: whatever the other session wrote would be lost. It propagates,
     * and the reader is told.
     */
    @Transactional
    public void update(String id, LinkDraft draft) {
        refuseDuplicate(draft.getUrl(), id);
        // A re-read that brought no bytes back is a page whose copy is now out of
        // date, so it is owed a fresh render. An edit that never re-read the page is
        // owed nothing: the archive it already has is still of the page it names.
        boolean reArchive = !carriesArchive(draft) && draft.isRefetched();
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
                existing.highlights(),
                reArchive ? ArchiveStatus.PENDING : existing.archiveStatus()));
        keepArchive(id, draft);
        if (reArchive) {
            archiveAfterCommit(new ArchiveRequest(id, owner.current(), draft.getUrl(),
                    draft.getTitle()));
        }
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
            // Nothing here has a page to render: these were saved before Harbor
            // archived anything, and the browser storage they come from kept only
            // what the reader had written. FAILED rather than PENDING, so an import
            // does not queue a render of every link in a library at once.
            BookmarkMapper.apply(bookmark.withArchiveStatus(ArchiveStatus.FAILED), entity,
                    owner.current());
            save(entity);
            imported++;
        }
        return imported;
    }

    /**
     * Only when the draft actually carries one. An edit that did not re-fetch has no
     * archive to offer, and the one already stored is the copy of the page — losing
     * it because a title was corrected would be the same mistake as losing the notes.
     */
    private void keepArchive(String bookmarkId, LinkDraft draft) {
        if (draft.getArchive() != null && draft.getArchive().length > 0) {
            archives.store(bookmarkId, draft.getArchive(), clock.millis());
        }
    }

    private static boolean carriesArchive(LinkDraft draft) {
        return draft.getArchive() != null && draft.getArchive().length > 0;
    }

    /**
     * Queued once the bookmark is actually there to be found. The render happens on
     * another thread and reads the row back to file its result against, so starting it
     * inside the transaction that created the row would have it looking for something
     * not yet committed.
     */
    private void archiveAfterCommit(ArchiveRequest request) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            backgroundArchiver.submit(request);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                backgroundArchiver.submit(request);
            }
        });
    }

    private void refuseDuplicate(String url, String allowedId) {
        repository.findByOwnerIdAndUrlKey(owner.current(), UrlKey.of(url))
                .filter(other -> !other.getId().toString().equals(allowedId))
                .ifPresent(other -> {
                    throw new DuplicateBookmarkException(BookmarkMapper.toBookmark(other));
                });
    }

    /**
     * Each of these is a function of whatever the bookmark currently says — flip
     * the flag, append the passage — so a version conflict can be answered by
     * reading again and recomputing. That retry cannot happen here, inside the
     * transaction the conflict just poisoned; the presenter calls again instead.
     */
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
