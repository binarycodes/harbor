package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.shared.Registration;

import io.binarycodes.harbor.library.domain.LibraryQuery;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.domain.SortMode;
import io.binarycodes.harbor.library.domain.ViewMode;

@DisplayName("How the reader is looking at their library")
class LibraryFilterTest {

    private LibraryFilter filter;
    private AtomicInteger changes;

    @BeforeEach
    void createFilter() {
        filter = new LibraryFilter();
        changes = new AtomicInteger();
        filter.addChangeListener(changes::incrementAndGet);
    }

    @Test
    @DisplayName("starts with nothing narrowed, newest first, as cards")
    void startsUnfiltered() {
        LibraryQuery query = filter.query(LibraryScope.ALL);

        assertFalse(filter.hasSelectedTags());
        assertEquals(Set.of(), query.tags());
        assertEquals("", query.searchText());
        assertEquals(SortMode.RECENT, filter.getSortMode());
        assertEquals(ViewMode.CARDS, filter.getViewMode());
    }

    @Test
    @DisplayName("tags toggle on and off")
    void togglesTags() {
        filter.toggleTag("Web");
        assertTrue(filter.isSelected("Web"));

        filter.toggleTag("Design");
        assertEquals(Set.of("Web", "Design"), filter.getSelectedTags());

        filter.toggleTag("Web");
        assertEquals(Set.of("Design"), filter.getSelectedTags());
        assertEquals(3, changes.get());
    }

    @Test
    @DisplayName("clearing tags does nothing when none are selected")
    void clearsTagsOnlyWhenNeeded() {
        filter.clearTags();
        assertEquals(0, changes.get());

        filter.toggleTag("Web");
        filter.clearTags();

        assertFalse(filter.hasSelectedTags());
        assertEquals(2, changes.get());
    }

    @Test
    @DisplayName("a tag whose last bookmark is gone stops being selected")
    void dropsTagsThatNoLongerExist() {
        filter.toggleTag("Web");
        filter.toggleTag("Obsolete");

        filter.retainTags(Set.of("Web"));

        assertEquals(Set.of("Web"), filter.getSelectedTags());
    }

    @Test
    @DisplayName("retaining tags that are all still there changes nothing")
    void keepsQuietWhenNothingWasDropped() {
        filter.toggleTag("Web");
        int before = changes.get();

        filter.retainTags(Set.of("Web", "Design"));

        assertEquals(before, changes.get());
    }

    @Test
    @DisplayName("search text is trimmed, and re-typing the same thing is not a change")
    void normalisesSearchText() {
        filter.setSearchText("  flexbox  ");
        assertEquals("flexbox", filter.getSearchText());
        assertEquals(1, changes.get());

        filter.setSearchText("flexbox");
        assertEquals(1, changes.get());

        filter.setSearchText(null);
        assertEquals("", filter.getSearchText());
        assertEquals(2, changes.get());
    }

    @Test
    @DisplayName("order and density are remembered, and only announced when they change")
    void remembersOrderAndDensity() {
        filter.setSortMode(SortMode.TITLE);
        filter.setSortMode(SortMode.TITLE);
        filter.setViewMode(ViewMode.COMPACT);
        filter.setViewMode(ViewMode.COMPACT);

        assertEquals(SortMode.TITLE, filter.getSortMode());
        assertEquals(ViewMode.COMPACT, filter.getViewMode());
        assertEquals(2, changes.get());
    }

    @Test
    @DisplayName("the query it hands out carries the scope it was asked for")
    void buildsQueryForScope() {
        filter.toggleTag("Web");
        filter.setSearchText("flex");
        filter.setSortMode(SortMode.READING_TIME);

        LibraryQuery query = filter.query(LibraryScope.READ_LATER);

        assertEquals(LibraryScope.READ_LATER, query.scope());
        assertEquals(Set.of("Web"), query.tags());
        assertEquals("flex", query.searchText());
        assertEquals(SortMode.READING_TIME, query.sortMode());
    }

    @Test
    @DisplayName("a removed listener stops hearing about changes")
    void stopsNotifyingRemovedListeners() {
        AtomicInteger extra = new AtomicInteger();
        Registration registration = filter.addChangeListener(extra::incrementAndGet);

        filter.toggleTag("Web");
        registration.remove();
        filter.toggleTag("Design");

        assertEquals(1, extra.get());
        assertEquals(2, changes.get());
    }
}
