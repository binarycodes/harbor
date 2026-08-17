package io.binarycodes.harbor.library.ui.component;

import java.util.List;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;

import io.binarycodes.harbor.library.domain.Bookmark;

/**
 * A bookmark list as a table, for when the reader wants to see as many rows at
 * once as the screen allows. All rows are rendered so the page scrolls as one
 * surface instead of trapping a second scrollbar inside the grid.
 */
public class BookmarkCompactGrid extends Grid<Bookmark> {

    public BookmarkCompactGrid(BookmarkActions actions) {
        super(Bookmark.class, false);
        addClassName("bookmark-compact-grid");
        setWidthFull();
        setAllRowsVisible(true);

        addColumn(new ComponentRenderer<>(this::titleCell))
                .setHeader(getTranslation("bookmark.column.title"))
                .setFlexGrow(1);
        addColumn(bookmark -> bookmark.tags().isEmpty()
                ? getTranslation("bookmark.column.tags.none")
                : String.join(", ", bookmark.tags()))
                .setHeader(getTranslation("bookmark.column.tags"))
                .setFlexGrow(0)
                .setWidth("150px");
        addColumn(bookmark -> getTranslation("bookmark.reading_time", bookmark.readingMinutes()))
                .setHeader(getTranslation("bookmark.column.reading_time"))
                .setTextAlign(ColumnTextAlign.END)
                .setFlexGrow(0)
                .setWidth("110px");
        addColumn(bookmark -> RelativeDate.label(this, bookmark.savedAt()))
                .setHeader(getTranslation("bookmark.column.saved"))
                .setTextAlign(ColumnTextAlign.END)
                .setFlexGrow(0)
                .setWidth("110px");

        addItemClickListener(event -> actions.open(event.getItem()));
    }

    public void setBookmarks(List<Bookmark> bookmarks) {
        setItems(bookmarks);
    }

    private Div titleCell(Bookmark bookmark) {
        Div stripe = new Div();
        stripe.addClassName("bookmark-compact-stripe");
        stripe.getStyle().set("background", "var(--color-cover-" + bookmark.coverIndex() + ")");

        Span title = new Span(bookmark.title());
        title.addClassName("bookmark-compact-title");
        Span site = new Span(bookmark.site());
        site.addClassName("bookmark-compact-site");

        Div text = new Div(title, site);
        text.addClassName("bookmark-compact-text");

        Div cell = new Div(stripe, text);
        cell.addClassName("bookmark-compact-cell");

        if (bookmark.readLater()) {
            Span queued = new Span();
            queued.addClassName("bookmark-compact-queued");
            queued.getElement().setAttribute("title", getTranslation("nav.read_later"));
            cell.add(queued);
        }
        return cell;
    }
}
