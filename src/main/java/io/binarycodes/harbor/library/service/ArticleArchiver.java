package io.binarycodes.harbor.library.service;

import java.util.Optional;

/**
 * Keeps a copy of a page that the reader's Markdown cannot be: one that still has
 * the pictures. Implementations reach the network for it, so this is never called
 * on the UI thread.
 *
 * <p>A URL and a title are the whole input. Harbor has usually already parsed the
 * page by the time this is called, but handing that parse over would be no use to
 * an implementation that has to fetch the page for itself anyway — and it is what
 * lets an archive be rendered long after the page was read.
 */
interface ArticleArchiver {

    /**
     * @return the archived page, or empty when there is nothing worth keeping or it
     *         would not render. Never throws: a page that cannot be archived is
     *         still a page worth saving.
     */
    Optional<byte[]> archive(String title, String url, long archivedAt);
}
