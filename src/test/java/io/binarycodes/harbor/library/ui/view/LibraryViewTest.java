package io.binarycodes.harbor.library.ui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.textfield.TextField;

import io.binarycodes.harbor.BrowserlessStorageConfiguration;
import io.binarycodes.harbor.HarborDatabase;
import io.binarycodes.harbor.StubMetadataConfiguration;
import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.BookmarkType;
import io.binarycodes.harbor.library.domain.LibraryQuery;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.domain.LinkDraft;
import io.binarycodes.harbor.library.domain.ViewMode;
import io.binarycodes.harbor.library.ui.component.BookmarkCard;
import io.binarycodes.harbor.library.ui.component.BookmarkRow;
import io.binarycodes.harbor.library.ui.component.DeleteBookmarkButton;
import io.binarycodes.harbor.library.ui.component.DeleteBookmarkDialog;
import io.binarycodes.harbor.library.ui.component.EditBookmarkButton;
import io.binarycodes.harbor.library.ui.component.SaveLinkDialog;
import io.binarycodes.harbor.library.ui.presenter.LibraryFilter;
import io.binarycodes.harbor.library.ui.presenter.LibraryPresenter;

@SpringBootTest
@ContextConfiguration(classes = { StubMetadataConfiguration.class, BrowserlessStorageConfiguration.class,
        HarborDatabase.class })
@DisplayName("The library screen")
@TestPropertySource(properties = "harbor.archive.browser-url=http://archiver.invalid:9222")
class LibraryViewTest extends SpringBrowserlessTest {

    /**
     * The presenter and the filter are session-scoped, and the session only exists
     * once the base class has built it — later than field injection would run. They
     * are looked up per test instead.
     */
    @Autowired
    private ApplicationContext applicationContext;

    private LibraryPresenter presenter;
    private LibraryFilter libraryFilter;

    @BeforeEach
    void startFromAnEmptyLibrary() {
        presenter = applicationContext.getBean(LibraryPresenter.class);
        libraryFilter = applicationContext.getBean(LibraryFilter.class);
        presenter.load();
        // One database for the whole suite, so what one test saved is still there
        // for the next. Each starts from the empty library a first visit sees.
        presenter.find(LibraryQuery.of(LibraryScope.ALL))
                .forEach(bookmark -> presenter.remove(bookmark.id()));
        libraryFilter.clearTags();
        libraryFilter.setSearchText("");
    }

    @Test
    @DisplayName("shows nothing but an invitation on a first visit")
    void showsEmptyStateOnFirstVisit() {
        navigate(LibraryView.class);

        assertEquals(0, cards().size());
    }

    @Test
    @DisplayName("shows a card for every saved bookmark")
    void showsACardPerBookmark() {
        save("https://example.com/one", "One");
        save("https://example.com/two", "Two");

        navigate(LibraryView.class);

        assertEquals(2, cards().size());
    }

    @Test
    @DisplayName("narrows to what the search box matches")
    void narrowsOnSearch() {
        save("https://example.com/flexbox", "An Interactive Guide to Flexbox");
        save("https://example.com/deep-work", "Deep Work");
        navigate(LibraryView.class);

        libraryFilter.setSearchText("flexbox");

        assertEquals(1, cards().size());
    }

    @Test
    @DisplayName("narrows to the selected tag")
    void narrowsOnTag() {
        save("https://example.com/one", "One", List.of("Web"));
        save("https://example.com/two", "Two", List.of("Science"));
        navigate(LibraryView.class);

        libraryFilter.toggleTag("Web");

        assertEquals(1, cards().size());
    }

    @Test
    @DisplayName("keeps read later to what was queued")
    void separatesReadLater() {
        save("https://example.com/one", "One");
        String queued = save("https://example.com/two", "Two");
        presenter.toggleReadLater(queued);

        navigate(ReadLaterView.class);

        assertEquals(1, cards().size());
    }

    @Test
    @DisplayName("survives a return visit, notes and highlights included")
    void restoresAcrossVisits() {
        String id = save("https://example.com/one", "One");
        presenter.updateNotes(id, "worth remembering");
        presenter.addHighlight(id, "a passage worth keeping");

        navigate(LibraryView.class);

        assertTrue(presenter.findById(id).orElseThrow().hasNotes());
        assertFalse(presenter.withHighlights().isEmpty());
        assertEquals(1, cards().size());
    }

    @Test
    @DisplayName("asks before deleting a bookmark, and keeps it if the question is declined")
    void keepsTheBookmarkWhenDeletingIsCancelled() {
        String id = save("https://example.com/one", "One");
        showRows();

        deleteButtons().getFirst().click();
        find(DeleteBookmarkDialog.class).single().close();

        assertTrue(presenter.findById(id).isPresent());
        assertEquals(1, rows().size());
    }

    /**
     * Which of the two goes depends on the sort order, so what is asserted is that
     * exactly one did — deleting one bookmark must not take its neighbour with it.
     */
    @Test
    @DisplayName("deletes only the chosen bookmark once the question is answered")
    void deletesTheBookmarkOnConfirmation() {
        String first = save("https://example.com/one", "One");
        String second = save("https://example.com/two", "Two");
        showRows();

        deleteButtons().getFirst().click();
        confirmDeletion();

        long surviving = List.of(first, second).stream()
                .filter(id -> presenter.findById(id).isPresent())
                .count();
        assertEquals(1, surviving);
        assertEquals(1, rows().size());
    }

    @Test
    @DisplayName("takes the notes and highlights with it")
    void deletesWhatTheReaderAdded() {
        String id = save("https://example.com/one", "One");
        presenter.updateNotes(id, "worth remembering");
        presenter.addHighlight(id, "a passage worth keeping");
        showRows();

        deleteButtons().getFirst().click();
        confirmDeletion();

        assertTrue(presenter.withHighlights().isEmpty());
        assertEquals(0, presenter.count());
    }

    /**
     * The gate that stops an unreachable link being filed as though it were an
     * article: nothing is saveable until the page behind the URL has been read.
     */
    @Test
    @DisplayName("refuses to save a link until its page has been fetched")
    void requiresAFetchBeforeSaving() throws InterruptedException {
        navigate(LibraryView.class);
        find(Button.class).withCaption("Save a link").single().click();
        SaveLinkDialog dialog = find(SaveLinkDialog.class).single();
        find(TextField.class, dialog).withLabel("URL").single().setValue("https://example.com/unread");

        assertFalse(submitButton(dialog).isEnabled());

        find(Button.class, dialog).withCaption("Fetch").single().click();
        awaitSubmitEnabled(dialog);

        assertTrue(submitButton(dialog).isEnabled());
        assertEquals(0, presenter.count());
    }

    /**
     * The other half of the gate. A page Harbor reached but could not archive must not
     * be saveable either — archiving is a primary objective, and a bookmark without a
     * copy of its page is a link that will rot.
     */
    @Test
    @DisplayName("refuses to save a link whose page could not be archived")
    void requiresAnArchiveBeforeSaving() throws InterruptedException {
        navigate(LibraryView.class);
        find(Button.class).withCaption("Save a link").single().click();
        SaveLinkDialog dialog = find(SaveLinkDialog.class).single();
        find(TextField.class, dialog).withLabel("URL").single()
                .setValue("https://example.com/" + StubMetadataConfiguration.UNARCHIVABLE);

        find(Button.class, dialog).withCaption("Fetch").single().click();
        awaitUrlRefused(dialog);

        assertFalse(submitButton(dialog).isEnabled());
        assertEquals(0, presenter.count());
    }

    /**
     * The refusal arrives through {@code ui.access} like the fetch itself, so the field
     * has to be looked up again each round rather than held on to.
     */
    private void awaitUrlRefused(SaveLinkDialog dialog) throws InterruptedException {
        for (int attempt = 0; attempt < 200 && !urlField(dialog).isInvalid(); attempt++) {
            Thread.sleep(10);
            roundTrip();
        }
        assertTrue(urlField(dialog).isInvalid(), "the URL should have been refused");
    }

    private TextField urlField(SaveLinkDialog dialog) {
        return find(TextField.class, dialog).withLabel("URL").single();
    }

    private Button submitButton(SaveLinkDialog dialog) {
        return find(Button.class, dialog).withCaption("Save to library").single();
    }

    /**
     * The fetch runs off the UI thread and hands its result back through
     * {@code ui.access}, so the button has to be looked up again each time rather than
     * held on to — a reference taken before the round trip does not see the change.
     */
    private void awaitSubmitEnabled(SaveLinkDialog dialog) throws InterruptedException {
        for (int attempt = 0; attempt < 200 && !submitButton(dialog).isEnabled(); attempt++) {
            Thread.sleep(10);
            roundTrip();
        }
    }

    @Test
    @DisplayName("edits a bookmark in place, keeping its notes and highlights")
    void editsABookmarkInPlace() {
        String id = save("https://example.com/one", "One");
        presenter.updateNotes(id, "worth remembering");
        presenter.addHighlight(id, "a passage worth keeping");
        showRows();

        findInView(EditBookmarkButton.class).all().getFirst().click();
        SaveLinkDialog dialog = find(SaveLinkDialog.class).single();
        find(TextField.class, dialog).withLabel("Title").single().setValue("One, corrected");
        find(Button.class, dialog).withCaption("Save changes").single().click();

        Bookmark edited = presenter.findById(id).orElseThrow();
        assertEquals("One, corrected", edited.title());
        assertEquals("worth remembering", edited.notes());
        assertEquals(1, edited.highlights().size());
        assertEquals(1, presenter.count());
    }

    /**
     * Vaadin's Card attaches its slots at element level, so nothing inside a card's
     * media or footer is reachable through the component tree. The row rendering shows
     * the same button as a plain child, and both come from the same
     * {@code BookmarkActions}.
     */
    private void showRows() {
        navigate(LibraryView.class);
        libraryFilter.setViewMode(ViewMode.ROWS);
    }

    /**
     * The dialog opens against the UI rather than the view, and its buttons live in its
     * own shadow layout — so it is looked up UI-wide and confirmed by firing the event
     * that button would fire.
     */
    private void confirmDeletion() {
        DeleteBookmarkDialog confirmation = find(DeleteBookmarkDialog.class).single();
        ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, true));
    }

    private List<DeleteBookmarkButton> deleteButtons() {
        return findInView(DeleteBookmarkButton.class).all();
    }

    private List<BookmarkRow> rows() {
        return findInView(BookmarkRow.class).all();
    }

    private List<BookmarkCard> cards() {
        return findInView(BookmarkCard.class).all();
    }

    private String save(String url, String title) {
        return save(url, title, List.of("Reading"));
    }

    private String save(String url, String title, List<String> tags) {
        LinkDraft draft = new LinkDraft();
        draft.setUrl(url);
        draft.setTitle(title);
        draft.setSite("example.com");
        draft.setDescription("Saved from example.com");
        draft.setType(BookmarkType.ARTICLE);
        draft.setReadingMinutes(7);
        draft.setContent("## Body\n\nSome words.");
        // Every bookmark carries an archive now; add() refuses a draft without one.
        draft.setArchive(StubMetadataConfiguration.STUB_ARCHIVE);
        draft.setTags(tags);
        return presenter.add(draft).id();
    }
}
