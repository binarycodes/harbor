package io.binarycodes.harbor.library.ui.view;

import java.util.Optional;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;

import io.binarycodes.harbor.base.ui.EmptyState;
import io.binarycodes.harbor.base.ui.MainLayout;
import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.service.BookmarkService;
import io.binarycodes.harbor.library.ui.component.DeleteBookmarkDialog;
import io.binarycodes.harbor.library.ui.component.ReaderArticle;
import io.binarycodes.harbor.library.ui.component.ReaderHeader;
import io.binarycodes.harbor.library.ui.component.ReaderSidePanel;

/**
 * One article, with the reader's notes and highlights beside it.
 *
 * <p>Arriving straight at this URL can outrun the library: the browser has not
 * answered with its stored bookmarks yet, so the requested one is resolved from a
 * change listener rather than during navigation. Only once the library is known can
 * a missing bookmark be reported as missing.
 *
 * <p>After that first render every edit refreshes just the part it touched. A
 * blanket redraw would re-render the article on each pause in note-taking, throwing
 * away the reader's place on the page.
 */
@Route(value = "read/:" + ReaderView.BOOKMARK_ID, layout = MainLayout.class)
public class ReaderView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    public static final String BOOKMARK_ID = "bookmarkId";

    private final BookmarkService bookmarkService;
    private final ReaderHeader header = new ReaderHeader(this::toggleReadLater, this::deleteBookmark);
    private final ReaderArticle article = new ReaderArticle(this::addHighlight);
    private final ReaderSidePanel sidePanel;
    private final EmptyState missing = new EmptyState();
    private final Div body = new Div();

    private String bookmarkId;
    private String pageTitle;
    private Registration libraryChanges;
    private boolean rendered;

    public ReaderView(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
        sidePanel = new ReaderSidePanel(this::updateNotes, this::removeHighlight);

        addClassName("reader-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        body.addClassName("reader-columns");
        body.add(article, sidePanel);
        add(header, body, missing);
        setFlexGrow(1, body);
        showLoading();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        bookmarkId = event.getRouteParameters().get(BOOKMARK_ID).orElse("");
        rendered = false;
        showLoading();
    }

    @Override
    public String getPageTitle() {
        return pageTitle == null ? getTranslation("app.name") : pageTitle;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        libraryChanges = bookmarkService.addChangeListener(this::resolveBookmark);
        bookmarkService.load();
        resolveBookmark();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (libraryChanges != null) {
            libraryChanges.remove();
            libraryChanges = null;
        }
        super.onDetach(detachEvent);
    }

    private void resolveBookmark() {
        if (rendered) {
            return;
        }
        Optional<Bookmark> found = bookmarkService.findById(bookmarkId);
        if (found.isPresent()) {
            rendered = true;
            show(found.get());
        } else if (bookmarkService.isLoaded()) {
            showMissing();
        }
    }

    private void show(Bookmark bookmark) {
        pageTitle = bookmark.title();
        header.show(bookmark);
        article.show(bookmark);
        sidePanel.show(bookmark);
        header.setVisible(true);
        body.setVisible(true);
        missing.setVisible(false);
    }

    private void showLoading() {
        header.setVisible(false);
        body.setVisible(false);
        missing.setVisible(false);
    }

    private void showMissing() {
        header.setVisible(false);
        body.setVisible(false);
        missing.setVisible(true);
        missing.update(VaadinIcon.BOOKMARK.create(), getTranslation("reader.missing.title"),
                getTranslation("reader.missing.hint"));
    }

    private void toggleReadLater() {
        bookmarkService.toggleReadLater(bookmarkId);
        current().ifPresent(header::show);
    }

    /**
     * Deleting the article being read leaves nothing to read, so the reader goes back
     * to the library rather than being left looking at a bookmark that no longer
     * exists.
     */
    private void deleteBookmark() {
        current().ifPresent(bookmark -> new DeleteBookmarkDialog(bookmark, () -> {
            bookmarkService.remove(bookmarkId);
            UI.getCurrent().navigate(LibraryView.class);
        }).open());
    }

    private void updateNotes(String notes) {
        bookmarkService.updateNotes(bookmarkId, notes);
        current().ifPresent(sidePanel::show);
    }

    private void addHighlight(String text) {
        bookmarkService.addHighlight(bookmarkId, text);
        current().ifPresent(bookmark -> {
            sidePanel.show(bookmark);
            sidePanel.selectHighlights();
            article.show(bookmark);
        });
    }

    private void removeHighlight(int index) {
        bookmarkService.removeHighlight(bookmarkId, index);
        current().ifPresent(bookmark -> {
            sidePanel.show(bookmark);
            article.show(bookmark);
        });
    }

    private Optional<Bookmark> current() {
        return bookmarkService.findById(bookmarkId);
    }
}
