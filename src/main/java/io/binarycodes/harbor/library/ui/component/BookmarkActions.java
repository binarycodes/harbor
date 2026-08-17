package io.binarycodes.harbor.library.ui.component;

import io.binarycodes.harbor.library.domain.Bookmark;

/**
 * What a listing can do to the bookmark under the pointer, whichever of the three
 * renderings is showing it.
 */
public interface BookmarkActions {

    void open(Bookmark bookmark);

    void toggleReadLater(Bookmark bookmark);

    /**
     * Asks to delete the bookmark. Whether that is confirmed first is the listing's
     * decision, not the button's.
     */
    void remove(Bookmark bookmark);
}
