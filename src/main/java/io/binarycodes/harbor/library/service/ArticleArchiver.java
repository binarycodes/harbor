package io.binarycodes.harbor.library.service;

import java.util.Optional;

import org.jsoup.nodes.Document;

/**
 * Keeps a copy of an article that the reader's Markdown cannot be: one that still
 * has the pictures. Implementations reach the network for those, so this is never
 * called on the UI thread.
 */
interface ArticleArchiver {

    /**
     * @return the archived article, or empty when there is nothing worth keeping or
     *         it would not render. Never throws: a page that cannot be archived is
     *         still a page worth saving.
     */
    Optional<byte[]> archive(Document document, String title, String url, long archivedAt);
}
