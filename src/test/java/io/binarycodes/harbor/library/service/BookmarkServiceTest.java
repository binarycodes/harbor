package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.shared.Registration;

import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.BookmarkType;
import io.binarycodes.harbor.library.domain.ColorSchemePreference;
import io.binarycodes.harbor.library.domain.Highlight;
import io.binarycodes.harbor.library.domain.LibraryQuery;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.domain.LinkDraft;
import io.binarycodes.harbor.library.domain.SortMode;
import io.binarycodes.harbor.library.domain.TagCount;

@DisplayName("The library")
class BookmarkServiceTest {

    private InMemoryLibraryStorage storage;
    private TestClock clock;
    private BookmarkService service;

    @BeforeEach
    void createService() {
        storage = new InMemoryLibraryStorage();
        clock = new TestClock();
        service = new BookmarkService(new BookmarkStore(storage), clock);
    }

    @Nested
    @DisplayName("on a first visit")
    class FirstVisit {

        @Test
        @DisplayName("starts empty rather than with sample data")
        void startsEmpty() {
            service.load();

            assertTrue(service.isLoaded());
            assertEquals(0, service.count());
            assertEquals(List.of(), service.find(LibraryQuery.of(LibraryScope.ALL)));
        }

        @Test
        @DisplayName("reports nothing to read later and no highlights")
        void reportsEmptyCounts() {
            service.load();

            assertEquals(0, service.countReadLater());
            assertEquals(0, service.countHighlights());
            assertEquals(List.of(), service.tagCounts());
        }

        @Test
        @DisplayName("leaves the color scheme to the operating system")
        void defaultsToSystemColorScheme() {
            service.load();

            assertEquals(ColorSchemePreference.SYSTEM, service.getColorScheme());
        }

        @Test
        @DisplayName("only asks the browser once")
        void readsStorageOnce() {
            AtomicInteger changes = new AtomicInteger();
            service.addChangeListener(changes::incrementAndGet);

            service.load();
            service.load();

            assertEquals(1, changes.get());
        }
    }

    @Nested
    @DisplayName("when a link is saved")
    class Saving {

        @Test
        @DisplayName("puts the newest bookmark first and writes it to storage")
        void addsNewestFirst() {
            service.load();

            save("https://example.com/one", "One");
            clock.advance(Duration.ofMinutes(5));
            save("https://example.com/two", "Two");

            List<Bookmark> found = service.find(LibraryQuery.of(LibraryScope.ALL));
            assertEquals(List.of("Two", "One"), found.stream().map(Bookmark::title).toList());
            assertTrue(storage.payload().contains("https://example.com/two"));
        }

        @Test
        @DisplayName("stamps the bookmark with the time it was saved")
        void stampsSavedAt() {
            service.load();

            Bookmark saved = save("https://example.com/one", "One");

            assertEquals(clock.millis(), saved.savedAt());
        }

        @Test
        @DisplayName("gives every bookmark its own identity")
        void assignsDistinctIds() {
            service.load();

            Bookmark first = save("https://example.com/one", "One");
            Bookmark second = save("https://example.com/one", "One again");

            assertFalse(first.id().equals(second.id()));
        }

        @Test
        @DisplayName("never records a reading time below a minute")
        void keepsReadingTimePositive() {
            service.load();
            LinkDraft draft = draft("https://example.com/short", "Short");
            draft.setReadingMinutes(0);

            assertEquals(1, service.add(draft).readingMinutes());
        }
    }

    @Nested
    @DisplayName("when filtering")
    class Filtering {

        @BeforeEach
        void fillLibrary() {
            service.load();
            LinkDraft flexbox = draft("https://joshwcomeau.com/flexbox", "An Interactive Guide to Flexbox");
            flexbox.setTags(List.of("Web", "Design"));
            service.add(flexbox);

            clock.advance(Duration.ofHours(1));
            LinkDraft localFirst = draft("https://inkandswitch.com/local-first", "Local-first software");
            localFirst.setTags(List.of("Web", "Research"));
            localFirst.setReadLater(true);
            service.add(localFirst);

            clock.advance(Duration.ofHours(1));
            LinkDraft deepWork = draft("https://calnewport.com/deep-work", "Deep Work");
            deepWork.setTags(List.of("Productivity"));
            service.add(deepWork);
        }

        @Test
        @DisplayName("read later shows only what was queued")
        void narrowsToReadLater() {
            List<Bookmark> found = service.find(LibraryQuery.of(LibraryScope.READ_LATER));

            assertEquals(List.of("Local-first software"), found.stream().map(Bookmark::title).toList());
        }

        @Test
        @DisplayName("several tags narrow together rather than widening")
        void requiresEverySelectedTag() {
            LibraryQuery query = LibraryQuery.of(LibraryScope.ALL).withTags(Set.of("Web", "Research"));

            List<Bookmark> found = service.find(query);

            assertEquals(List.of("Local-first software"), found.stream().map(Bookmark::title).toList());
        }

        @Test
        @DisplayName("search ignores case and looks at descriptions")
        void searchesDescriptions() {
            LibraryQuery query = LibraryQuery.of(LibraryScope.ALL).withSearchText("SAVED FROM");

            assertEquals(3, service.find(query).size());
        }

        @Test
        @DisplayName("search reaches the reader's own notes")
        void searchesNotes() {
            String id = service.find(LibraryQuery.of(LibraryScope.ALL)).getFirst().id();
            service.updateNotes(id, "Try the rhythmic philosophy");

            LibraryQuery query = LibraryQuery.of(LibraryScope.ALL).withSearchText("rhythmic");

            assertEquals(List.of("Deep Work"), service.find(query).stream().map(Bookmark::title).toList());
        }

        @Test
        @DisplayName("search reaches saved highlights")
        void searchesHighlights() {
            String id = service.find(LibraryQuery.of(LibraryScope.ALL)).getFirst().id();
            service.addHighlight(id, "increasingly rare and increasingly valuable");

            LibraryQuery query = LibraryQuery.of(LibraryScope.ALL).withSearchText("increasingly valuable");

            assertEquals(List.of("Deep Work"), service.find(query).stream().map(Bookmark::title).toList());
        }

        @Test
        @DisplayName("a search that matches nothing returns nothing")
        void findsNothingForAnUnknownTerm() {
            LibraryQuery query = LibraryQuery.of(LibraryScope.ALL).withSearchText("kryptonite");

            assertEquals(List.of(), service.find(query));
        }

        @Test
        @DisplayName("sorts by title, by reading time, and by recency")
        void sortsEveryWay() {
            LibraryQuery base = LibraryQuery.of(LibraryScope.ALL);

            assertEquals(
                    List.of("An Interactive Guide to Flexbox", "Deep Work", "Local-first software"),
                    titles(base.withSortMode(SortMode.TITLE)));
            assertEquals(
                    List.of("Deep Work", "Local-first software", "An Interactive Guide to Flexbox"),
                    titles(base.withSortMode(SortMode.RECENT)));
            assertEquals(3, service.find(base.withSortMode(SortMode.READING_TIME)).size());
        }

        @Test
        @DisplayName("counts tags most-used first")
        void countsTags() {
            List<TagCount> counts = service.tagCounts();

            assertEquals("Web", counts.getFirst().name());
            assertEquals(2, counts.getFirst().count());
            assertEquals(4, counts.size());
        }

        private List<String> titles(LibraryQuery query) {
            return service.find(query).stream().map(Bookmark::title).toList();
        }
    }

    @Nested
    @DisplayName("when a bookmark is edited")
    class Editing {

        private String id;

        @BeforeEach
        void saveOne() {
            service.load();
            id = save("https://example.com/one", "One").id();
        }

        @Test
        @DisplayName("read later toggles both ways")
        void togglesReadLater() {
            service.toggleReadLater(id);
            assertTrue(service.findById(id).orElseThrow().readLater());

            service.toggleReadLater(id);
            assertFalse(service.findById(id).orElseThrow().readLater());
        }

        @Test
        @DisplayName("notes are kept")
        void keepsNotes() {
            service.updateNotes(id, "## Why it matters");

            assertEquals("## Why it matters", service.findById(id).orElseThrow().notes());
            assertTrue(service.findById(id).orElseThrow().hasNotes());
        }

        @Test
        @DisplayName("highlights are added in the order they were made and can be removed")
        void collectsHighlights() {
            service.addHighlight(id, "first passage");
            service.addHighlight(id, "second passage");

            assertEquals(2, service.countHighlights());
            assertEquals(List.of("first passage", "second passage"),
                    service.findById(id).orElseThrow().highlights().stream().map(Highlight::text).toList());

            service.removeHighlight(id, 0);

            assertEquals(List.of("second passage"),
                    service.findById(id).orElseThrow().highlights().stream().map(Highlight::text).toList());
        }

        @Test
        @DisplayName("a blank selection is not a highlight")
        void ignoresBlankHighlights() {
            service.addHighlight(id, "   ");
            service.addHighlight(id, null);

            assertEquals(0, service.countHighlights());
        }

        @Test
        @DisplayName("removing a highlight that is not there changes nothing")
        void ignoresOutOfRangeRemoval() {
            service.addHighlight(id, "only passage");

            service.removeHighlight(id, 7);
            service.removeHighlight(id, -1);

            assertEquals(1, service.countHighlights());
        }

        @Test
        @DisplayName("editing an unknown bookmark is a no-op")
        void ignoresUnknownIds() {
            service.updateNotes("no-such-id", "ignored");
            service.toggleReadLater("no-such-id");

            assertEquals("", service.findById(id).orElseThrow().notes());
            assertTrue(service.findById("no-such-id").isEmpty());
        }

        @Test
        @DisplayName("a removed bookmark is gone")
        void removesBookmark() {
            service.remove(id);

            assertEquals(0, service.count());
        }

        @Test
        @DisplayName("removing an unknown bookmark writes nothing")
        void ignoresUnknownRemoval() {
            int writesBefore = storage.writeCount();

            service.remove("no-such-id");

            assertEquals(writesBefore, storage.writeCount());
        }

        @Test
        @DisplayName("only bookmarks with highlights show up on the highlights screen")
        void listsOnlyAnnotatedBookmarks() {
            assertEquals(List.of(), service.withHighlights());

            service.addHighlight(id, "a passage");

            assertEquals(1, service.withHighlights().size());
        }
    }

    @Nested
    @DisplayName("across visits")
    class Persistence {

        @Test
        @DisplayName("restores what the previous visit saved")
        void restoresStoredLibrary() {
            service.load();
            save("https://example.com/one", "One");
            service.setColorScheme(ColorSchemePreference.DARK);

            BookmarkService reopened = new BookmarkService(new BookmarkStore(storage), clock);
            reopened.load();

            assertEquals(1, reopened.count());
            assertEquals("One", reopened.find(LibraryQuery.of(LibraryScope.ALL)).getFirst().title());
            assertEquals(ColorSchemePreference.DARK, reopened.getColorScheme());
        }

        @Test
        @DisplayName("keeps notes and highlights")
        void restoresAnnotations() {
            service.load();
            String id = save("https://example.com/one", "One").id();
            service.updateNotes(id, "remember this");
            service.addHighlight(id, "a passage worth keeping");

            BookmarkService reopened = new BookmarkService(new BookmarkStore(storage), clock);
            reopened.load();

            Bookmark restored = reopened.findById(id).orElseThrow();
            assertEquals("remember this", restored.notes());
            assertEquals(List.of("a passage worth keeping"),
                    restored.highlights().stream().map(Highlight::text).toList());
        }

        @Test
        @DisplayName("starts over rather than failing on an unreadable payload")
        void toleratesUnreadableStorage() {
            BookmarkService withRubbish = new BookmarkService(
                    new BookmarkStore(new InMemoryLibraryStorage("{ this is not our json")), clock);

            withRubbish.load();

            assertTrue(withRubbish.isLoaded());
            assertEquals(0, withRubbish.count());
        }
    }

    @Nested
    @DisplayName("change notifications")
    class Notifications {

        @Test
        @DisplayName("fire on every change and stop once the listener is removed")
        void notifyUntilRemoved() {
            AtomicInteger changes = new AtomicInteger();
            Registration registration = service.addChangeListener(changes::incrementAndGet);

            service.load();
            save("https://example.com/one", "One");
            int whileListening = changes.get();

            registration.remove();
            save("https://example.com/two", "Two");

            assertTrue(whileListening >= 2);
            assertEquals(whileListening, changes.get());
        }
    }

    private Bookmark save(String url, String title) {
        return service.add(draft(url, title));
    }

    private LinkDraft draft(String url, String title) {
        LinkDraft draft = new LinkDraft();
        draft.setUrl(url);
        draft.setTitle(title);
        draft.setSite("example.com");
        draft.setDescription("Saved from example.com");
        draft.setType(BookmarkType.ARTICLE);
        draft.setReadingMinutes(7);
        draft.setContent("## Body\n\nSome words.");
        return draft;
    }
}
