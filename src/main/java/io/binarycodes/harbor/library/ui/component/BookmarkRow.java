package io.binarycodes.harbor.library.ui.component;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import io.binarycodes.harbor.library.domain.Bookmark;

/**
 * A bookmark as a wide row: the same information as the card, laid out for
 * scanning a long list rather than browsing a grid.
 *
 * <p>Unlike the card this is a plain flex row rather than a horizontal Card. The
 * row needs the date and the queue toggle pinned to its right edge while the title
 * truncates, and that means owning the layout rather than negotiating with the
 * card's own header slots.
 */
public class BookmarkRow extends HorizontalLayout {

    public BookmarkRow(Bookmark bookmark, BookmarkActions actions) {
        addClassName("bookmark-row");
        getElement().setAttribute("role", "listitem");
        setWidthFull();
        setPadding(false);
        setAlignItems(Alignment.CENTER);

        Span date = new Span(RelativeDate.label(this, bookmark.savedAt()));
        date.addClassName("bookmark-row-date");

        Div text = text(bookmark);
        add(CoverTile.forSite(bookmark.site()), text, date, new ReadLaterButton(bookmark, actions),
                new DeleteBookmarkButton(() -> actions.remove(bookmark)));
        setFlexGrow(1, text);

        getElement().addEventListener("click", event -> actions.open(bookmark));
    }

    private Div text(Bookmark bookmark) {
        H3 title = new H3(bookmark.title());
        title.addClassName("bookmark-row-title");
        Span description = new Span(bookmark.description());
        description.addClassName("bookmark-row-description");

        Div text = new Div(title, description, details(bookmark));
        text.addClassName("bookmark-row-text");
        return text;
    }

    private Div details(Bookmark bookmark) {
        Span site = new Span(VaadinIcon.GLOBE.create(), new Span(bookmark.site()));
        site.addClassName("bookmark-metadata-item");
        Span readingTime = new Span(getTranslation("bookmark.reading_time", bookmark.readingMinutes()));
        readingTime.addClassName("bookmark-metadata-item");

        Div details = new Div(site, readingTime, new TagChips(bookmark.tags()));
        details.addClassName("bookmark-row-details");

        if (bookmark.readLater()) {
            Badge queued = new Badge(getTranslation("nav.read_later"));
            queued.addClassName("bookmark-row-queued");
            details.add(queued);
        }
        return details;
    }
}
