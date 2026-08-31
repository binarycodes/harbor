package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import io.binarycodes.harbor.HarborDatabase;
import io.binarycodes.harbor.StubIdentityConfiguration;
import io.binarycodes.harbor.StubMetadataConfiguration;
import io.binarycodes.harbor.library.domain.ArchiveStatus;
import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.BookmarkSummary;
import io.binarycodes.harbor.library.domain.BookmarkType;
import io.binarycodes.harbor.library.domain.Highlight;
import io.binarycodes.harbor.library.domain.LibraryQuery;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.domain.LinkDraft;
import io.binarycodes.harbor.library.domain.SortMode;
import io.binarycodes.harbor.library.domain.TagCount;

@SpringBootTest
@Import({ HarborDatabase.class, StubIdentityConfiguration.class, BookmarkServiceTest.FixedClock.class })
@DisplayName("The library")
@ActiveProfiles("test")
class BookmarkServiceTest {

    @Autowired
    private BookmarkService service;

    @Autowired
    private BookmarkRepository repository;

    @Autowired
    private BookmarkArchiveService archives;

    @Autowired
    private StubIdentityConfiguration identity;

    @Autowired
    private Clock injectedClock;

    private TestClock clock;

    @BeforeEach
    void startFromAnEmptyLibrary() {
        repository.deleteAll();
        identity.actAs(StubIdentityConfiguration.READER);
        clock = (TestClock) injectedClock;
        clock.reset();
    }

    /**
     * Saved timestamps have to be predictable and distinct, which the real clock
     * cannot promise for two writes in the same millisecond.
     */
    @TestConfiguration
    static class FixedClock {

        @Bean
        @Primary
        Clock testClock() {
            return new TestClock();
        }
    }

    @Nested
    @DisplayName("on a first visit")
    class FirstVisit {

        @Test
        @DisplayName("starts empty rather than with sample data")
        void startsEmpty() {
            assertEquals(0, service.count());
            assertEquals(List.of(), service.find(LibraryQuery.of(LibraryScope.ALL)));
        }

        @Test
        @DisplayName("reports nothing to read later and no highlights")
        void reportsEmptyCounts() {
            assertEquals(0, service.countReadLater());
            assertEquals(0, service.countHighlights());
            assertEquals(List.of(), service.tagCounts());
        }

    }

    @Nested
    @DisplayName("when a link is saved")
    class Saving {

        @Test
        @DisplayName("puts the newest bookmark first")
        void addsNewestFirst() {
            save("https://example.com/one", "One");
            clock.advance(Duration.ofMinutes(5));
            save("https://example.com/two", "Two");

            List<BookmarkSummary> found = service.find(LibraryQuery.of(LibraryScope.ALL));
            assertEquals(List.of("Two", "One"), found.stream().map(BookmarkSummary::title).toList());
        }

        @Test
        @DisplayName("stamps the bookmark with the time it was saved")
        void stampsSavedAt() {
            Bookmark saved = save("https://example.com/one", "One");

            assertEquals(clock.millis(), saved.savedAt());
        }

        /**
         * Two different links, because the same one twice is refused outright — see
         * {@link Duplicates}. The identity still has to be the bookmark's own rather
         * than anything derived from what it points at.
         */
        @Test
        @DisplayName("gives every bookmark its own identity")
        void assignsDistinctIds() {
            Bookmark first = save("https://example.com/one", "One");
            Bookmark second = save("https://example.com/two", "Two");

            assertFalse(first.id().equals(second.id()));
        }

        @Test
        @DisplayName("never records a reading time below a minute")
        void keepsReadingTimePositive() {
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
            List<BookmarkSummary> found = service.find(LibraryQuery.of(LibraryScope.READ_LATER));

            assertEquals(List.of("Local-first software"), found.stream().map(BookmarkSummary::title).toList());
        }

        @Test
        @DisplayName("several tags narrow together rather than widening")
        void requiresEverySelectedTag() {
            LibraryQuery query = LibraryQuery.of(LibraryScope.ALL).withTags(Set.of("Web", "Research"));

            List<BookmarkSummary> found = service.find(query);

            assertEquals(List.of("Local-first software"), found.stream().map(BookmarkSummary::title).toList());
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

            assertEquals(List.of("Deep Work"), service.find(query).stream().map(BookmarkSummary::title).toList());
        }

        @Test
        @DisplayName("search reaches saved highlights")
        void searchesHighlights() {
            String id = service.find(LibraryQuery.of(LibraryScope.ALL)).getFirst().id();
            service.addHighlight(id, "increasingly rare and increasingly valuable");

            LibraryQuery query = LibraryQuery.of(LibraryScope.ALL).withSearchText("increasingly valuable");

            assertEquals(List.of("Deep Work"), service.find(query).stream().map(BookmarkSummary::title).toList());
        }

        @Test
        @DisplayName("a search that matches nothing returns nothing")
        void findsNothingForAnUnknownTerm() {
            LibraryQuery query = LibraryQuery.of(LibraryScope.ALL).withSearchText("kryptonite");

            assertEquals(List.of(), service.find(query));
        }

        @Test
        @DisplayName("sorts by title and by recency")
        void sortsEveryWay() {
            LibraryQuery base = LibraryQuery.of(LibraryScope.ALL);

            assertEquals(
                    List.of("An Interactive Guide to Flexbox", "Deep Work", "Local-first software"),
                    titles(base.withSortMode(SortMode.TITLE)));
            assertEquals(
                    List.of("Deep Work", "Local-first software", "An Interactive Guide to Flexbox"),
                    titles(base.withSortMode(SortMode.RECENT)));
        }

        @Test
        @DisplayName("counts tags most-used first")
        void countsTags() {
            List<TagCount> counts = service.tagCounts();

            assertEquals("Web", counts.getFirst().name());
            assertEquals(2, counts.getFirst().count());
            assertEquals(4, counts.size());
        }

    }

    @Nested
    @DisplayName("when a bookmark is edited")
    class Editing {

        private String id;

        @BeforeEach
        void saveOne() {
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
        @DisplayName("removing an unknown bookmark changes nothing")
        void ignoresUnknownRemoval() {
            service.remove("no-such-id");
            service.remove("not-even-a-uuid");

            assertEquals(1, service.count());
        }

        @Test
        @DisplayName("only bookmarks with highlights show up on the highlights screen")
        void listsOnlyAnnotatedBookmarks() {
            assertEquals(List.of(), service.withHighlights());

            service.addHighlight(id, "a passage");

            assertEquals(1, service.withHighlights().size());
        }
    }

    /**
     * All of this is the shipped default, where the archive is rendered before the save
     * returns. What the same drafts do with {@code harbor.archive.force-before-save}
     * off is {@link BackgroundArchiveTest}.
     */
    @Nested
    @DisplayName("when a page could not be archived")
    class Unarchivable {

        /**
         * Archiving is the point, not a bonus: a bookmark with no copy of its page is a
         * link that will rot, so the service refuses one rather than filing it.
         */
        @Test
        @DisplayName("is refused rather than saved without its archive")
        void refusesADraftWithNoArchive() {
            LinkDraft draft = draft("https://example.com/unarchivable", "Unarchivable");
            draft.setArchive(null);

            assertThrows(IllegalArgumentException.class, () -> service.add(draft));
            assertEquals(0, service.count());
        }

        @Test
        @DisplayName("is refused for an empty archive as well as a missing one")
        void refusesAnEmptyArchive() {
            LinkDraft draft = draft("https://example.com/empty", "Empty");
            draft.setArchive(new byte[0]);

            assertThrows(IllegalArgumentException.class, () -> service.add(draft));
        }

        /**
         * Editing is the one exception. Correcting a title carries no fresh archive, and
         * requiring one would mean re-rendering the page on every edit — and losing the
         * ability to edit at all once the page has gone.
         */
        @Test
        @DisplayName("does not stop an edit, which keeps the archive already stored")
        void allowsAnEditWithoutAFreshArchive() {
            String id = save("https://example.com/one", "One").id();
            LinkDraft edit = draft("https://example.com/one", "One, corrected");
            edit.setArchive(null);

            service.update(id, edit);

            assertEquals("One, corrected", service.findById(id).orElseThrow().title());
        }
    }

    @Nested
    @DisplayName("when a library saved before the database is taken in")
    class Importing {

        @Test
        @DisplayName("keeps what the reader had written on it")
        void importsAnnotations() {
            Bookmark saved = new Bookmark("ignored", "https://example.com/one", "One", "example.com",
                    "example.com", "Saved from example.com", List.of("Reading"), BookmarkType.ARTICLE,
                    true, 1_600_000_000_000L, 7, "## Body", "remember this",
                    List.of(new Highlight("a passage worth keeping")), ArchiveStatus.READY);

            assertEquals(1, service.importAll(List.of(saved)));

            BookmarkSummary restored = service.find(LibraryQuery.of(LibraryScope.ALL)).getFirst();
            assertEquals("One", restored.title());
            assertTrue(restored.hasNotes());
            assertTrue(restored.readLater());
            assertEquals(1_600_000_000_000L, restored.savedAt());
            assertEquals(1, restored.highlightCount());
        }

        /**
         * The id in the old payload was the browser's to invent. The database
         * assigns its own, and the reader reaches a bookmark through the library
         * rather than by remembering one.
         */
        @Test
        @DisplayName("gives the bookmark the database's own identity")
        void assignsAFreshId() {
            Bookmark saved = imported("https://example.com/one", "One");

            service.importAll(List.of(saved));

            assertFalse(service.find(LibraryQuery.of(LibraryScope.ALL)).getFirst().id().equals("ignored"));
        }

        /**
         * Skipped rather than refused: an import that stops halfway because one link
         * had been saved again is worse than one that steps over it.
         */
        @Test
        @DisplayName("steps over anything already saved")
        void skipsWhatIsAlreadyThere() {
            save("https://example.com/one", "One");

            int taken = service.importAll(List.of(
                    imported("https://example.com/one", "One again"),
                    imported("https://example.com/two", "Two")));

            assertEquals(1, taken);
            assertEquals(2, service.count());
            assertEquals("One", service.findByUrl("https://example.com/one").orElseThrow().title());
        }

        private Bookmark imported(String url, String title) {
            return new Bookmark("ignored", url, title, "example.com", "example.com",
                    "Saved from example.com", List.of("Reading"), BookmarkType.ARTICLE, false,
                    1_600_000_000_000L, 7, "## Body", "", List.of(), ArchiveStatus.READY);
        }
    }

    /**
     * The shared fixture gives every bookmark the same reading time, which is why
     * these build their own set: an order can only be asserted against lengths that
     * actually differ.
     */
    @Nested
    @DisplayName("when sorting by how long a read is")
    class ReadingTimeOrder {

        @BeforeEach
        void saveThreeLengths() {
            saveMinutes("https://example.com/medium", "Medium", 9);
            saveMinutes("https://example.com/long", "Long", 30);
            saveMinutes("https://example.com/short", "Short", 2);
        }

        @Test
        @DisplayName("shortest first puts the quickest read at the top")
        void sortsShortestFirst() {
            assertEquals(List.of("Short", "Medium", "Long"), titles(
                    LibraryQuery.of(LibraryScope.ALL).withSortMode(SortMode.READING_TIME_SHORTEST)));
        }

        @Test
        @DisplayName("longest first turns the same list around")
        void sortsLongestFirst() {
            assertEquals(List.of("Long", "Medium", "Short"), titles(
                    LibraryQuery.of(LibraryScope.ALL).withSortMode(SortMode.READING_TIME_LONGEST)));
        }

        private void saveMinutes(String url, String title, int minutes) {
            LinkDraft draft = draft(url, title);
            draft.setReadingMinutes(minutes);
            service.add(draft);
        }
    }

    @Nested
    @DisplayName("saving a link that is already saved")
    class Duplicates {

        @Test
        @DisplayName("is refused, and names the entry that already holds it")
        void refusesTheSameUrlTwice() {
            Bookmark first = save("https://example.com/one", "One");

            DuplicateBookmarkException refused = assertThrows(DuplicateBookmarkException.class,
                    () -> save("https://example.com/one", "One again"));

            assertEquals(first.id(), refused.getExisting().id());
            assertEquals(1, service.count());
        }

        /**
         * The ways the same page gets written differently. None of these change which
         * page is fetched, so none of them should buy a second copy.
         */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "https://example.com/one/",
                "https://EXAMPLE.com/one",
                "https://example.com:443/one",
                "https://example.com/one#section"
        })
        @DisplayName("is refused however the URL is dressed up")
        void refusesEquivalentUrls(String equivalent) {
            save("https://example.com/one", "One");

            assertThrows(DuplicateBookmarkException.class, () -> save(equivalent, "One again"));
        }

        /**
         * Each of these can serve a different page, so refusing them would be worse
         * than keeping two entries.
         */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "http://example.com/one",
                "https://www.example.com/one",
                "https://example.com/one?page=2",
                "https://example.com/two"
        })
        @DisplayName("is allowed for URLs that only look similar")
        void allowsGenuinelyDifferentUrls(String different) {
            save("https://example.com/one", "One");

            assertTrue(service.findByUrl(different).isEmpty());
            service.add(draft(different, "Another"));

            assertEquals(2, service.count());
        }

        @Test
        @DisplayName("does not stop an edit that leaves the URL alone")
        void allowsEditingWithoutChangingTheUrl() {
            String id = save("https://example.com/one", "One").id();

            service.update(id, draft("https://example.com/one", "One, corrected"));

            assertEquals("One, corrected", service.findById(id).orElseThrow().title());
        }

        @Test
        @DisplayName("stops an edit that would collide with another entry")
        void refusesAnEditOntoAnotherUrl() {
            String id = save("https://example.com/one", "One").id();
            save("https://example.com/two", "Two");

            assertThrows(DuplicateBookmarkException.class,
                    () -> service.update(id, draft("https://example.com/two", "One")));
            assertEquals("One", service.findById(id).orElseThrow().title());
        }
    }

    /**
     * Every query is scoped by owner and none of that was ever asserted, because until
     * now there was only one owner to assert with. This is what those predicates are
     * for.
     */
    @Nested
    @DisplayName("belonging to another reader")
    class AnotherReader {

        @Test
        @DisplayName("is invisible in the listing, the counts and the tags")
        void isInvisible() {
            LinkDraft tagged = draft("https://example.com/one", "One");
            tagged.setTags(List.of("Research"));
            String id = service.add(tagged).id();
            service.addHighlight(id, "A passage worth keeping");
            service.toggleReadLater(id);

            identity.actAs(StubIdentityConfiguration.OTHER_READER);

            assertEquals(List.of(), service.find(LibraryQuery.of(LibraryScope.ALL)));
            assertEquals(0, service.count());
            assertEquals(0, service.countReadLater());
            assertEquals(0, service.countHighlights());
            assertEquals(List.of(), service.tagCounts());
            assertEquals(List.of(), service.withHighlights());
        }

        @Test
        @DisplayName("cannot be reached by its id or its URL")
        void cannotBeFetched() {
            String id = save("https://example.com/one", "One").id();

            identity.actAs(StubIdentityConfiguration.OTHER_READER);

            assertTrue(service.findById(id).isEmpty());
            assertTrue(service.findByUrl("https://example.com/one").isEmpty());
        }

        @Test
        @DisplayName("survives another reader trying to delete it")
        void cannotBeDeleted() {
            String id = save("https://example.com/one", "One").id();

            identity.actAs(StubIdentityConfiguration.OTHER_READER);
            service.remove(id);

            identity.actAs(StubIdentityConfiguration.READER);
            assertEquals("One", service.findById(id).orElseThrow().title());
        }

        @Test
        @DisplayName("keeps its notes and highlights out of another reader's edits")
        void cannotBeEdited() {
            String id = save("https://example.com/one", "One").id();

            identity.actAs(StubIdentityConfiguration.OTHER_READER);
            service.updateNotes(id, "Notes from someone else");
            service.addHighlight(id, "A passage from someone else");
            service.toggleReadLater(id);

            identity.actAs(StubIdentityConfiguration.READER);
            Bookmark theirs = service.findById(id).orElseThrow();
            assertEquals("", theirs.notes());
            assertEquals(List.of(), theirs.highlights());
            assertFalse(theirs.readLater());
        }

        @Test
        @DisplayName("keeps its archive to itself")
        void keepsItsArchive() {
            String id = save("https://example.com/one", "One").id();
            assertTrue(archives.exists(id));

            identity.actAs(StubIdentityConfiguration.OTHER_READER);

            assertFalse(archives.exists(id));
            assertTrue(archives.findBytes(id).isEmpty());
        }

        /**
         * The unique index is on {@code (owner_id, url_key)}, so the same article in two
         * libraries is two rows rather than a collision. Worth asserting: the duplicate
         * check reads as global until you look at what it is scoped by.
         */
        @Test
        @DisplayName("can save the same URL without colliding")
        void savesTheSameUrl() {
            save("https://example.com/one", "One");

            identity.actAs(StubIdentityConfiguration.OTHER_READER);
            Bookmark mine = save("https://example.com/one", "One, mine");

            assertEquals(1, service.count());
            assertEquals("One, mine", service.findById(mine.id()).orElseThrow().title());

            identity.actAs(StubIdentityConfiguration.READER);
            assertEquals(1, service.count());
            assertEquals("One", service.findByUrl("https://example.com/one").orElseThrow().title());
        }
    }

    private List<String> titles(LibraryQuery query) {
        return service.find(query).stream().map(BookmarkSummary::title).toList();
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
        // add() refuses a draft with no archive while it is the save that renders one.
        draft.setArchive(StubMetadataConfiguration.STUB_ARCHIVE);
        return draft;
    }
}
