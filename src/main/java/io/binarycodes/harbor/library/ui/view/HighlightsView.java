package io.binarycodes.harbor.library.ui.view;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.shared.Registration;

import io.binarycodes.harbor.base.ui.EmptyState;
import io.binarycodes.harbor.base.ui.MainLayout;
import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.service.BookmarkService;
import io.binarycodes.harbor.library.ui.component.HighlightGroupCard;

/**
 * Every passage the reader has kept, gathered from all their bookmarks.
 */
@Route(value = "highlights", layout = MainLayout.class)
public class HighlightsView extends VerticalLayout implements HasDynamicTitle {

    private final BookmarkService bookmarkService;
    private final Div groups = new Div();
    private final EmptyState emptyState = new EmptyState();
    private final List<Registration> registrations = new ArrayList<>();

    public HighlightsView(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;

        addClassName("highlights-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        groups.addClassName("highlight-groups");
        add(groups, emptyState);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("highlights.title");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        registrations.add(bookmarkService.addChangeListener(this::refresh));
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
        List<Bookmark> annotated = bookmarkService.withHighlights();
        groups.removeAll();
        annotated.forEach(bookmark -> groups.add(new HighlightGroupCard(bookmark, this::open)));

        boolean nothingToShow = annotated.isEmpty() && bookmarkService.isLoaded();
        groups.setVisible(!annotated.isEmpty());
        emptyState.setVisible(nothingToShow);
        if (nothingToShow) {
            emptyState.update(VaadinIcon.QUOTE_RIGHT.create(), getTranslation("highlights.empty.title"),
                    getTranslation("highlights.empty.hint"));
        }
    }

    private void open(Bookmark bookmark) {
        UI.getCurrent().navigate(ReaderView.class,
                new RouteParameters(ReaderView.BOOKMARK_ID, bookmark.id()));
    }
}
