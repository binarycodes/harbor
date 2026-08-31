package io.binarycodes.harbor.library.service;

import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.binarycodes.harbor.library.domain.ArchiveStatus;
import io.binarycodes.harbor.library.domain.BookmarkType;
import io.binarycodes.harbor.library.domain.Highlight;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A bookmark as the database holds it. Deliberately not the {@code Bookmark}
 * record the rest of Harbor passes around: JPA needs something it can construct
 * empty and mutate, and the domain is better off immutable. {@link BookmarkMapper}
 * is the only thing that translates between the two.
 *
 * <p>Package-private, along with the repository, so that the boundary is the
 * compiler's to enforce — a view that reaches for an entity does not build. That
 * also keeps every entity inside the service's transaction, which is what spares
 * a stateful UI framework from detached-entity merges.
 */
@Entity
@Table(name = "bookmark")
@Getter
@Setter
@NoArgsConstructor
class BookmarkEntity {

    @Id
    @GeneratedValue
    private UUID id;

    /**
     * Optimistic locking, because one reader can have several sessions open on the
     * same library — a second tab, a phone. It doubles as how Spring Data tells a new
     * row from an existing one: a null version has never been saved.
     */
    @Version
    private Long version;

    private String ownerId;
    private String url;
    private String urlKey;
    private String title;
    private String site;
    private String author;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> tags;

    @Enumerated(EnumType.STRING)
    private BookmarkType type;

    @Enumerated(EnumType.STRING)
    private ArchiveStatus archiveStatus;

    private boolean readLater;
    private long savedAt;
    private int readingMinutes;

    @Column(columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Highlight> highlights;

    @Column(columnDefinition = "text")
    private String searchText;
}
