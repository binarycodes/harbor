package io.binarycodes.harbor.library.service;

import java.util.List;

import io.binarycodes.harbor.library.domain.BookmarkType;

/**
 * What could be learned about a URL before saving it: how the page describes
 * itself, and the reader-ready version of its text.
 */
public record LinkMetadata(
        String site,
        String title,
        String description,
        List<String> tags,
        BookmarkType type,
        int readingMinutes,
        String content) {

    public LinkMetadata {
        tags = tags == null ? List.of() : List.copyOf(tags);
        type = type == null ? BookmarkType.ARTICLE : type;
        description = description == null ? "" : description;
        content = content == null ? "" : content;
    }
}
