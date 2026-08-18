package io.binarycodes.harbor.library.ui.component;

import java.util.List;

import com.vaadin.flow.component.html.Div;

import io.binarycodes.harbor.library.domain.BookmarkSummary;
import io.binarycodes.harbor.library.domain.ViewMode;

/**
 * The list of bookmarks in whichever of the three densities the reader picked.
 * The table keeps its identity between redraws so that scroll position and column
 * widths survive; the card and row renderings are cheap enough to rebuild.
 */
public class BookmarkListing extends Div {

    private final BookmarkActions actions;
    private final BookmarkCompactGrid compactGrid;

    public BookmarkListing(BookmarkActions actions) {
        this.actions = actions;
        compactGrid = new BookmarkCompactGrid(actions);
        addClassName("bookmark-listing");
        getElement().setAttribute("role", "list");
    }

    public void show(List<BookmarkSummary> bookmarks, ViewMode mode) {
        removeAll();
        setClassName("bookmark-listing-cards", mode == ViewMode.CARDS);
        setClassName("bookmark-listing-rows", mode == ViewMode.ROWS);
        switch (mode) {
            case CARDS -> bookmarks.forEach(bookmark -> add(new BookmarkCard(bookmark, actions)));
            case ROWS -> bookmarks.forEach(bookmark -> add(new BookmarkRow(bookmark, actions)));
            case COMPACT -> {
                compactGrid.setBookmarks(bookmarks);
                add(compactGrid);
            }
        }
    }
}
