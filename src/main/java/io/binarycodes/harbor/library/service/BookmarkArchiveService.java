package io.binarycodes.harbor.library.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.binarycodes.harbor.library.domain.ArchiveStatus;

/**
 * The archived PDFs, and how far each one has got. Only bytes cross out of here, so
 * nothing above ever holds a managed archive — and the largest thing Harbor stores
 * stays inside the transaction that read it.
 *
 * <p>Each write comes in two forms: one for a reader who is signed in, and one that
 * is told whose library it is working in. The second is for the background archiver,
 * which finishes a render on a thread that has no reader on it — and where
 * {@link LibraryOwner} is right to refuse rather than guess.
 */
@Component
public class BookmarkArchiveService {

    static final String PDF = "application/pdf";

    private final BookmarkArchiveRepository repository;
    private final BookmarkRepository bookmarks;
    private final LibraryOwner owner;

    BookmarkArchiveService(BookmarkArchiveRepository repository, BookmarkRepository bookmarks,
            LibraryOwner owner) {
        this.repository = repository;
        this.bookmarks = bookmarks;
        this.owner = owner;
    }

    /**
     * Keeps an archive against a bookmark, replacing whatever was there. A re-fetch
     * produces a fresh copy of a page that may well have changed, and the newer one
     * is the one worth keeping.
     */
    @Transactional
    public void store(String bookmarkId, byte[] pdf, long createdAt) {
        store(bookmarkId, owner.current(), pdf, createdAt);
    }

    /**
     * The same, for the background archiver, which has to be told whose library it is
     * writing into. Public because Spring's transaction proxy silently ignores a
     * non-public {@code @Transactional} method, not because anything above the service
     * layer has business calling it — an {@code ownerId} is not something a screen can
     * come by.
     */
    @Transactional
    public void store(String bookmarkId, String ownerId, byte[] pdf, long createdAt) {
        UUID key = BookmarkMapper.idOf(bookmarkId);
        if (key == null || pdf == null || pdf.length == 0) {
            return;
        }
        BookmarkArchiveEntity entity = repository
                .findByOwnerIdAndBookmarkId(ownerId, key)
                .orElseGet(BookmarkArchiveEntity::new);
        entity.setBookmarkId(key);
        entity.setOwnerId(ownerId);
        entity.setContentType(PDF);
        entity.setByteSize(pdf.length);
        entity.setCreatedAt(createdAt);
        entity.setBytes(pdf);
        repository.save(entity);
    }

    /**
     * Says how far a bookmark's archive has got. Separate from {@link #store} because
     * the two failing states have no bytes to write, and because a render that
     * produced nothing still has to stop the reader waiting for it.
     */
    @Transactional
    public void markStatus(String bookmarkId, String ownerId, ArchiveStatus status) {
        UUID key = BookmarkMapper.idOf(bookmarkId);
        if (key != null) {
            bookmarks.updateArchiveStatus(key, ownerId, status.name());
        }
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> findBytes(String bookmarkId) {
        UUID key = BookmarkMapper.idOf(bookmarkId);
        return key == null
                ? Optional.empty()
                : repository.findByOwnerIdAndBookmarkId(owner.current(), key)
                        .map(BookmarkArchiveEntity::getBytes);
    }

    @Transactional(readOnly = true)
    public boolean exists(String bookmarkId) {
        UUID key = BookmarkMapper.idOf(bookmarkId);
        return key != null && repository.existsByOwnerIdAndBookmarkId(owner.current(), key);
    }
}
