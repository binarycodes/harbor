package io.binarycodes.harbor.library.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface BookmarkArchiveRepository extends JpaRepository<BookmarkArchiveEntity, UUID> {

    Optional<BookmarkArchiveEntity> findByOwnerIdAndBookmarkId(String ownerId, UUID bookmarkId);

    boolean existsByOwnerIdAndBookmarkId(String ownerId, UUID bookmarkId);
}
