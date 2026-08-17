package io.binarycodes.harbor.library.domain;

import java.util.Set;

/**
 * Everything the toolbar and the sidebar contribute to what a listing shows.
 * Selected tags narrow together: a bookmark must carry all of them.
 */
public record LibraryQuery(LibraryScope scope, Set<String> tags, String searchText, SortMode sortMode) {

    public LibraryQuery {
        scope = scope == null ? LibraryScope.ALL : scope;
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        searchText = searchText == null ? "" : searchText.strip();
        sortMode = sortMode == null ? SortMode.RECENT : sortMode;
    }

    public static LibraryQuery of(LibraryScope scope) {
        return new LibraryQuery(scope, Set.of(), "", SortMode.RECENT);
    }

    public boolean hasSearchText() {
        return !searchText.isEmpty();
    }

    public boolean hasTags() {
        return !tags.isEmpty();
    }

    public LibraryQuery withTags(Set<String> value) {
        return new LibraryQuery(scope, value, searchText, sortMode);
    }

    public LibraryQuery withSearchText(String value) {
        return new LibraryQuery(scope, tags, value, sortMode);
    }

    public LibraryQuery withSortMode(SortMode value) {
        return new LibraryQuery(scope, tags, searchText, value);
    }
}
