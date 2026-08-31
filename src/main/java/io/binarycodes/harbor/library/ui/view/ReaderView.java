package io.binarycodes.harbor.library.ui.view;

import java.util.Optional;

import jakarta.annotation.security.PermitAll;

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
import io.binarycodes.harbor.library.domain.ArchiveStatus;
import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.service.BookmarkArchiveService;
import io.binarycodes.harbor.library.ui.component.DeleteBookmarkDialog;
import io.binarycodes.harbor.library.ui.component.ReaderArticle;
import io.binarycodes.harbor.library.ui.component.ReaderHeader;
import io.binarycodes.harbor.library.ui.component.ReaderSidePanel;
import io.binarycodes.harbor.library.ui.component.SaveLinkDialog;
import io.binarycodes.harbor.library.ui.presenter.LibraryPresenter;

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
@PermitAll
@Route(value = "read/:" + ReaderView.BOOKMARK_ID, layout = MainLayout.class)
public class ReaderView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    public static final String BOOKMARK_ID = "bookmarkId";

    private final LibraryPresenter presenter;
    private final BookmarkArchiveService archives;
    private final ReaderHeader header = new ReaderHeader(this::toggleReadLater, this::editBookmark,
            this::deleteBookmark);
    private final ReaderArticle article = new ReaderArticle(this::addHighlight);
    private final ReaderSidePanel sidePanel;
    private final EmptyState missing = new EmptyState();
    private final Div body = new Div();

    private final SaveLinkDialog editDialog;

    private String bookmarkId;
    private String pageTitle;
    private Registration libraryChanges;
    private boolean rendered;

    public ReaderView(LibraryPresenter presenter, BookmarkArchiveService archives) {
        this.presenter = presenter;
        this.archives = archives;
        sidePanel = new ReaderSidePanel(this::updateNotes, this::removeHighlight);
        // Saving an edit redraws the article in place: the change listener cannot, because
        // it deliberately leaves a rendered article alone while notes are being typed.
        editDialog = new SaveLinkDialog(presenter, this::show);

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
        libraryChanges = presenter.addChangeListener(this::resolveBookmark);
        presenter.load();
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
        Optional<Bookmark> found = presenter.findById(bookmarkId);
        if (found.isPresent()) {
            rendered = true;
            show(found.get());
        } else if (presenter.isLoaded()) {
            showMissing();
        }
    }

    private void show(Bookmark bookmark) {
        pageTitle = bookmark.title();
        // Flow reads getPageTitle() when it navigates, and an edit is not a navigation:
        // without this the tab keeps the title the article had before it was corrected.
        getUI().ifPresent(ui -> ui.getPage().setTitle(pageTitle));
        header.show(bookmark);
        showArchive(bookmark);
        article.show(bookmark);
        sidePanel.show(bookmark);
        header.setVisible(true);
        body.setVisible(true);
        missing.setVisible(false);
    }

    /**
     * The bytes are fetched only if the reader clicks: opening an article should not
     * read a PDF nobody asked for, and it is the largest thing stored.
     *
     * <p>Whether there is a copy to offer and whether one is still coming are two
     * questions. A bookmark saved before its render finished has neither; one whose
     * page was just re-read has both.
     */
    private void showArchive(Bookmark bookmark) {
        if (archives.exists(bookmark.id())) {
            header.showArchive(bookmark.title(),
                    () -> archives.findBytes(bookmark.id()).orElseGet(() -> new byte[0]));
        } else {
            header.hideArchive();
        }
        header.setArchivePending(bookmark.archiveStatus() == ArchiveStatus.PENDING);
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
        presenter.toggleReadLater(bookmarkId);
        current().ifPresent(header::show);
    }

    private void editBookmark() {
        current().ifPresent(editDialog::openFor);
    }

    /**
     * Deleting the article being read leaves nothing to read, so the reader goes back
     * to the library rather than being left looking at a bookmark that no longer
     * exists.
     */
    private void deleteBookmark() {
        current().ifPresent(bookmark -> new DeleteBookmarkDialog(bookmark.title(), () -> {
            presenter.remove(bookmarkId);
            UI.getCurrent().navigate(LibraryView.class);
        }).open());
    }

    private void updateNotes(String notes) {
        presenter.updateNotes(bookmarkId, notes);
        current().ifPresent(sidePanel::show);
    }

    private void addHighlight(String text) {
        presenter.addHighlight(bookmarkId, text);
        current().ifPresent(bookmark -> {
            sidePanel.show(bookmark);
            sidePanel.selectHighlights();
            article.show(bookmark);
        });
    }

    private void removeHighlight(int index) {
        presenter.removeHighlight(bookmarkId, index);
        current().ifPresent(bookmark -> {
            sidePanel.show(bookmark);
            article.show(bookmark);
        });
    }

    private Optional<Bookmark> current() {
        return presenter.findById(bookmarkId);
    }
}
