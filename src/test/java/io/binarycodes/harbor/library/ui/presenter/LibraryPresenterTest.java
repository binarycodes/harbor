package io.binarycodes.harbor.library.ui.presenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.vaadin.flow.shared.Registration;

import io.binarycodes.harbor.BrowserlessStorageConfiguration;
import io.binarycodes.harbor.HarborDatabase;
import io.binarycodes.harbor.StubIdentityConfiguration;
import io.binarycodes.harbor.StubMetadataConfiguration;
import io.binarycodes.harbor.base.ui.BrowserStorage;
import io.binarycodes.harbor.library.domain.BookmarkType;
import io.binarycodes.harbor.library.domain.LibraryQuery;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.domain.LinkDraft;
import io.binarycodes.harbor.library.service.BookmarkService;
import io.binarycodes.harbor.library.service.LegacyLibraryDecoder;

/**
 * The presenter owns telling the screens that something changed, so that is what
 * these cover, along with the one-off import of a library this browser was still
 * holding. What each change does to the library itself is the service's own test.
 *
 * <p>Built by hand rather than injected: the presenter is session-scoped and there
 * is no Vaadin session here.
 */
@SpringBootTest
@Import({ HarborDatabase.class, BrowserlessStorageConfiguration.class, StubIdentityConfiguration.class })
@DisplayName("The library presenter")
@ActiveProfiles("test")
class LibraryPresenterTest {

    private static final String LEGACY_KEY = "harbor.library.v1";

    @Autowired
    private BookmarkService bookmarkService;

    @Autowired
    private BrowserStorage browserStorage;

    @Autowired
    private LegacyLibraryDecoder decoder;

    private LibraryPresenter presenter;

    @BeforeEach
    void startFromAnEmptyLibrary() {
        browserStorage.remove(LEGACY_KEY);
        bookmarkService.find(LibraryQuery.of(LibraryScope.ALL))
                .forEach(bookmark -> bookmarkService.remove(bookmark.id()));
        presenter = new LibraryPresenter(bookmarkService,
                url -> {
                    throw new UnsupportedOperationException("No test here reads a page");
                },
                browserStorage,
                new LegacyLibraryImport(browserStorage, decoder, bookmarkService));
    }

    @Test
    @DisplayName("tells the screens once the library is settled, and only once")
    void notifiesOnLoadOnce() {
        AtomicInteger changes = new AtomicInteger();
        presenter.addChangeListener(changes::incrementAndGet);

        presenter.load();
        int afterFirst = changes.get();
        presenter.load();

        assertTrue(presenter.isLoaded());
        assertEquals(afterFirst, changes.get());
    }

    @Test
    @DisplayName("tells them on every change, and stops once the listener is removed")
    void notifiesUntilRemoved() {
        AtomicInteger changes = new AtomicInteger();
        Registration registration = presenter.addChangeListener(changes::incrementAndGet);

        presenter.load();
        save("https://example.com/one", "One");
        int whileListening = changes.get();

        registration.remove();
        save("https://example.com/two", "Two");

        assertTrue(whileListening >= 2);
        assertEquals(whileListening, changes.get());
    }

    /**
     * The upgrade that would otherwise lose a reader's library. The old payload is the
     * shape Harbor wrote before there was a database.
     */
    @Test
    @DisplayName("takes in a library this browser was still holding, and says how many")
    void importsWhatTheBrowserKept() {
        browserStorage.write(LEGACY_KEY, """
                {"bookmarks":[{"id":"a-browser-id","url":"https://example.com/kept",\
                "title":"Kept","site":"example.com","author":"example.com",\
                "description":"From before","tags":["Reading"],"type":"ARTICLE",\
                "readLater":false,"savedAt":1600000000000,"readingMinutes":7,\
                "content":"## Body","notes":"worth remembering","highlights":[{"text":"a passage"}]}],\
                "colorScheme":"DARK"}""");

        presenter.load();

        assertEquals(1, presenter.importedFromBrowser());
        assertEquals(1, presenter.count());
        // The notes came across; a listing reports only that there are some, so the
        // whole bookmark is read back to see them.
        String id = presenter.find(LibraryQuery.of(LibraryScope.ALL)).getFirst().id();
        assertEquals("worth remembering", presenter.findById(id).orElseThrow().notes());
    }

    /**
     * Cleared only once it has been taken, so the same library is not imported
     * again on the next visit — and is still there if the import failed.
     */
    @Test
    @DisplayName("clears the old storage once it has taken it")
    void clearsTheLegacyKeyAfterImporting() {
        browserStorage.write(LEGACY_KEY, """
                {"bookmarks":[{"id":"a-browser-id","url":"https://example.com/kept","title":"Kept",\
                "site":"example.com","author":"example.com","description":"From before",\
                "tags":[],"type":"ARTICLE","readLater":false,"savedAt":1600000000000,\
                "readingMinutes":7,"content":"## Body","notes":"","highlights":[]}]}""");
        presenter.load();

        AtomicInteger stillThere = new AtomicInteger();
        browserStorage.read(LEGACY_KEY, value -> stillThere.set(value == null ? 0 : 1));

        assertEquals(0, stillThere.get());
    }

    @Test
    @DisplayName("says nothing was imported for a browser that never ran the older version")
    void importsNothingWhenTheBrowserHasNothing() {
        presenter.load();

        assertEquals(0, presenter.importedFromBrowser());
        assertTrue(presenter.isLoaded());
    }

    private void save(String url, String title) {
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
        draft.setTags(List.of("Reading"));
        presenter.add(draft);
    }
}
