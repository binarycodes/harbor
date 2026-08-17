package io.binarycodes.harbor.library.ui.component;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.shared.Registration;

import io.binarycodes.harbor.base.ui.EmptyState;
import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.LibraryQuery;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.service.BookmarkService;
import io.binarycodes.harbor.library.service.LibraryFilter;
import io.binarycodes.harbor.library.ui.view.ReaderView;

/**
 * A library listing with its toolbar, shared by the "all bookmarks" and "read
 * later" screens — the two differ only in which bookmarks they let through.
 */
public class LibraryContent extends VerticalLayout implements BookmarkActions, HasDynamicTitle {

    private final LibraryScope scope;
    private final BookmarkService bookmarkService;
    private final LibraryFilter libraryFilter;
    private final LibraryToolbar toolbar;
    private final ActiveTagChips activeTagChips;
    private final BookmarkListing listing = new BookmarkListing(this);
    private final EmptyState emptyState = new EmptyState();
    private final List<Registration> registrations = new ArrayList<>();

    protected LibraryContent(LibraryScope scope, BookmarkService bookmarkService, LibraryFilter libraryFilter) {
        this.scope = scope;
        this.bookmarkService = bookmarkService;
        this.libraryFilter = libraryFilter;
        toolbar = new LibraryToolbar(libraryFilter);
        activeTagChips = new ActiveTagChips(libraryFilter);

        addClassName("library-content");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        Div body = new Div(activeTagChips, listing, emptyState);
        body.addClassName("library-body");
        add(toolbar, body);
        setFlexGrow(1, body);
    }

    @Override
    public String getPageTitle() {
        return getTranslation(scope.titleKey());
    }

    @Override
    public void open(Bookmark bookmark) {
        UI.getCurrent().navigate(ReaderView.class,
                new RouteParameters(ReaderView.BOOKMARK_ID, bookmark.id()));
    }

    @Override
    public void toggleReadLater(Bookmark bookmark) {
        bookmarkService.toggleReadLater(bookmark.id());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        registrations.add(bookmarkService.addChangeListener(this::refresh));
        registrations.add(libraryFilter.addChangeListener(this::refresh));
        bookmarkService.load();
        refresh();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        registrations.forEach(Registration::remove);
        registrations.clear();
        super.onDetach(detachEvent);
    }

    private void refresh() {
        LibraryQuery query = libraryFilter.query(scope);
        List<Bookmark> found = bookmarkService.find(query);

        toolbar.setHeading(getTranslation(scope.titleKey()), summary(query, found.size()));
        toolbar.refresh();
        activeTagChips.refresh();

        // Until the browser has answered with what it stored, an empty listing means
        // "not known yet", not "nothing saved" — showing either would be a guess.
        boolean nothingToShow = found.isEmpty() && bookmarkService.isLoaded();
        listing.setVisible(!found.isEmpty());
        emptyState.setVisible(nothingToShow);
        if (nothingToShow) {
            showEmptyState(query);
        } else {
            listing.show(found, libraryFilter.getViewMode());
        }
    }

    private String summary(LibraryQuery query, int count) {
        String items = count == 1
                ? getTranslation("library.count.one")
                : getTranslation("library.count.many", count);
        if (query.hasSearchText()) {
            return getTranslation("library.summary.search", items, query.searchText());
        }
        if (scope == LibraryScope.READ_LATER) {
            return getTranslation("library.summary.read_later", items);
        }
        return items;
    }

    private void showEmptyState(LibraryQuery query) {
        if (query.hasSearchText() || query.hasTags()) {
            emptyState.update(VaadinIcon.SEARCH.create(), getTranslation("library.empty.filtered.title"),
                    getTranslation("library.empty.filtered.hint"));
        } else if (scope == LibraryScope.READ_LATER) {
            emptyState.update(VaadinIcon.CLOCK.create(), getTranslation("library.empty.read_later.title"),
                    getTranslation("library.empty.read_later.hint"));
        } else {
            emptyState.update(VaadinIcon.BOOKMARK.create(), getTranslation("library.empty.all.title"),
                    getTranslation("library.empty.all.hint"));
        }
    }
}
