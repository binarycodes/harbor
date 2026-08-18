package io.binarycodes.harbor.library.ui.component;

import io.binarycodes.harbor.library.domain.BookmarkSummary;

/**
 * What a listing can do to the bookmark under the pointer, whichever of the three
 * renderings is showing it.
 */
public interface BookmarkActions {

    void open(BookmarkSummary bookmark);

    void toggleReadLater(BookmarkSummary bookmark);

    /**
     * Reopens the save dialog over the bookmark so its details can be corrected.
     */
    void edit(BookmarkSummary bookmark);

    /**
     * Asks to delete the bookmark. Whether that is confirmed first is the listing's
     * decision, not the button's.
     */
    void remove(BookmarkSummary bookmark);
}
