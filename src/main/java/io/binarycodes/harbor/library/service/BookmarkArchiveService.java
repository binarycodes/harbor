package io.binarycodes.harbor.library.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The archived PDFs. Only bytes cross out of here, so nothing above ever holds a
 * managed archive — and the largest thing Harbor stores stays inside the
 * transaction that read it.
 */
@Component
public class BookmarkArchiveService {

    static final String PDF = "application/pdf";

    private final BookmarkArchiveRepository repository;
    private final LibraryOwner owner;

    BookmarkArchiveService(BookmarkArchiveRepository repository, LibraryOwner owner) {
        this.repository = repository;
        this.owner = owner;
    }

    /**
     * Keeps an archive against a bookmark, replacing whatever was there. A re-fetch
     * produces a fresh copy of a page that may well have changed, and the newer one
     * is the one worth keeping.
     */
    @Transactional
    public void store(String bookmarkId, byte[] pdf, long createdAt) {
        UUID key = BookmarkMapper.idOf(bookmarkId);
        if (key == null || pdf == null || pdf.length == 0) {
            return;
        }
        BookmarkArchiveEntity entity = repository
                .findByOwnerIdAndBookmarkId(owner.current(), key)
                .orElseGet(BookmarkArchiveEntity::new);
        entity.setBookmarkId(key);
        entity.setOwnerId(owner.current());
        entity.setContentType(PDF);
        entity.setByteSize(pdf.length);
        entity.setCreatedAt(createdAt);
        entity.setBytes(pdf);
        repository.save(entity);
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
