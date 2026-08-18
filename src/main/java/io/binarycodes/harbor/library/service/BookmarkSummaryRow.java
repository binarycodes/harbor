package io.binarycodes.harbor.library.service;

/**
 * One listing row as the database returns it. The aliases in the query are quoted
 * so they survive PostgreSQL's habit of folding unquoted identifiers to lower
 * case, which is what lets these getters match them.
 *
 * <p>The tags arrive as the jsonb text rather than a list — the driver has no
 * reason to know what Harbor keeps in there, so {@link BookmarkMapper} decodes it.
 */
interface BookmarkSummaryRow {

    String getId();

    String getTitle();

    String getSite();

    String getDescription();

    String getTags();

    String getType();

    boolean getReadLater();

    long getSavedAt();

    int getReadingMinutes();

    int getHighlightCount();

    boolean getHasNotes();
}
