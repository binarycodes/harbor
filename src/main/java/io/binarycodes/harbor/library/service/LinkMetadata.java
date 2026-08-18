package io.binarycodes.harbor.library.service;

import java.util.List;

import io.binarycodes.harbor.library.domain.BookmarkType;

/**
 * What could be learned about a URL before saving it: how the page describes
 * itself, the reader-ready version of its text, and the PDF kept alongside it.
 *
 * @param pageRead whether any of this came from the page itself. False means the
 *                 page could not be read and everything here was inferred from the
 *                 URL alone, which is not enough to save a bookmark on.
 * @param archive  the article as a PDF, or empty when the page would not render.
 *                 An absent archive never stops a link being saved.
 */
public record LinkMetadata(
        String site,
        String title,
        String description,
        List<String> tags,
        BookmarkType type,
        int readingMinutes,
        String content,
        boolean pageRead,
        byte[] archive) {

    public LinkMetadata {
        tags = tags == null ? List.of() : List.copyOf(tags);
        type = type == null ? BookmarkType.ARTICLE : type;
        description = description == null ? "" : description;
        content = content == null ? "" : content;
        archive = archive == null ? new byte[0] : archive;
    }

    public boolean hasArchive() {
        return archive.length > 0;
    }
}
