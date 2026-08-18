package io.binarycodes.harbor.library.service;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An archived PDF. Its own entity with no association to {@link BookmarkEntity},
 * joined only by the id they share: modelling it as a relation would put the whole
 * archive one mis-set fetch type away from being loaded with every bookmark, and
 * two unrelated entities cannot go wrong that way.
 */
@Entity
@Table(name = "bookmark_archive")
@Getter
@Setter
@NoArgsConstructor
class BookmarkArchiveEntity {

    /**
     * The bookmark's own id, not a generated one. There is at most one archive per
     * bookmark, so a second key would only allow a second archive.
     */
    @Id
    private UUID bookmarkId;

    private String ownerId;
    private String contentType;
    private int byteSize;
    private long createdAt;

    /**
     * Plain {@code byte[]}, never {@code @Lob}: on PostgreSQL that maps to an
     * {@code oid} large object rather than {@code bytea}.
     */
    @Column(columnDefinition = "bytea")
    private byte[] bytes;
}
