package io.binarycodes.harbor.library.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The library's queries. Native rather than JPQL because the three that matter
 * all reach for something only PostgreSQL offers: {@code @>} against the jsonb
 * tags, a trigram {@code like} over the search text, and ICU collation on the
 * title.
 *
 * <p>Every parameter is cast explicitly. PostgreSQL will not infer the type of a
 * bound parameter that appears only beside another parameter, and the error when
 * it cannot is a long way from the cause.
 */
interface BookmarkRepository extends JpaRepository<BookmarkEntity, UUID> {

    Optional<BookmarkEntity> findByOwnerIdAndId(String ownerId, UUID id);

    Optional<BookmarkEntity> findByOwnerIdAndUrlKey(String ownerId, String urlKey);

    long countByOwnerId(String ownerId);

    long countByOwnerIdAndReadLaterTrue(String ownerId);

    /**
     * The listing. A scope, a set of tags that all have to be present, and a
     * substring of anything searchable — each one skipped when it was not asked
     * for, so that one query serves every combination the toolbar can produce.
     */
    @Query(value = """
            select b.* from bookmark b
            where b.owner_id = cast(:ownerId as text)
              and (cast(:readLaterOnly as boolean) = false or b.read_later = true)
              and (cast(:tags as jsonb) = '[]'::jsonb or b.tags @> cast(:tags as jsonb))
              and (cast(:searchText as text) = ''
                   or b.search_text like '%' || cast(:searchText as text) || '%')
            """, nativeQuery = true)
    List<BookmarkEntity> findMatching(@Param("ownerId") String ownerId,
            @Param("readLaterOnly") boolean readLaterOnly,
            @Param("tags") String tags,
            @Param("searchText") String searchText,
            Sort sort);

    @Query(value = """
            select b.* from bookmark b
            where b.owner_id = cast(:ownerId as text)
              and jsonb_array_length(b.highlights) > 0
            order by b.saved_at desc
            """, nativeQuery = true)
    List<BookmarkEntity> findAnnotated(@Param("ownerId") String ownerId);

    @Query(value = """
            select coalesce(sum(jsonb_array_length(b.highlights)), 0) from bookmark b
            where b.owner_id = cast(:ownerId as text)
            """, nativeQuery = true)
    long countHighlights(@Param("ownerId") String ownerId);

    /**
     * Most used first, then alphabetically — and alphabetically means ICU, so that
     * "Ökonomie" sorts where a reader expects rather than after "Zoology".
     */
    @Query(value = """
            select tag as name, count(*) as count
            from bookmark b, jsonb_array_elements_text(b.tags) as tag
            where b.owner_id = cast(:ownerId as text)
            group by tag
            order by count(*) desc, tag collate "und-x-icu"
            """, nativeQuery = true)
    List<TagCountRow> tagCounts(@Param("ownerId") String ownerId);
}
