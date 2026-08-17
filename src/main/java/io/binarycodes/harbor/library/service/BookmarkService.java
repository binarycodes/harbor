package io.binarycodes.harbor.library.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

import com.ibm.icu.text.Collator;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;

import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.ColorSchemePreference;
import io.binarycodes.harbor.library.domain.Highlight;
import io.binarycodes.harbor.library.domain.LibraryQuery;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.domain.LinkDraft;
import io.binarycodes.harbor.library.domain.TagCount;

/**
 * The library as the UI sees it. Held per session and mirrored to the browser on
 * every change; {@link #load()} has to finish before the contents are known,
 * because the browser answers asynchronously, so screens wait for the first
 * change event instead of reading straight after construction.
 */
@Component
@VaadinSessionScope
public class BookmarkService {

    private final BookmarkStore store;
    private final Clock clock;
    private final List<Bookmark> bookmarks = new ArrayList<>();
    private final List<Runnable> changeListeners = new ArrayList<>();

    private ColorSchemePreference colorScheme = ColorSchemePreference.SYSTEM;
    private boolean loaded;
    private boolean loadRequested;
    private transient Collator titleCollator;

    public BookmarkService(BookmarkStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /**
     * Asks the browser for the stored library, once per session.
     */
    public void load() {
        if (loadRequested) {
            return;
        }
        loadRequested = true;
        store.read(this::apply);
    }

    public boolean isLoaded() {
        return loaded;
    }

    public Registration addChangeListener(Runnable listener) {
        changeListeners.add(listener);
        return () -> changeListeners.remove(listener);
    }

    public List<Bookmark> find(LibraryQuery query) {
        String searchTerm = query.searchText().toLowerCase(Locale.ROOT);
        return bookmarks.stream()
                .filter(bookmark -> query.scope() != LibraryScope.READ_LATER || bookmark.readLater())
                .filter(bookmark -> bookmark.tags().containsAll(query.tags()))
                .filter(bookmark -> searchTerm.isEmpty()
                        || bookmark.searchableText().toLowerCase(Locale.ROOT).contains(searchTerm))
                .sorted(comparatorFor(query))
                .toList();
    }

    public Optional<Bookmark> findById(String id) {
        return bookmarks.stream().filter(bookmark -> bookmark.id().equals(id)).findFirst();
    }

    public List<Bookmark> withHighlights() {
        return bookmarks.stream().filter(Bookmark::hasHighlights).toList();
    }

    public int count() {
        return bookmarks.size();
    }

    public int countReadLater() {
        return (int) bookmarks.stream().filter(Bookmark::readLater).count();
    }

    public int countHighlights() {
        return bookmarks.stream().mapToInt(bookmark -> bookmark.highlights().size()).sum();
    }

    /**
     * Tags in the order the sidebar shows them: most used first, then alphabetical.
     */
    public List<TagCount> tagCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        bookmarks.forEach(bookmark -> bookmark.tags()
                .forEach(tag -> counts.merge(tag, 1, Integer::sum)));
        return counts.entrySet().stream()
                .map(entry -> new TagCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(TagCount::count).reversed()
                        .thenComparing(TagCount::name, collator()))
                .toList();
    }

    /**
     * The same page saved twice is a mistake worth refusing rather than silently
     * keeping both, since the second copy carries none of the notes or highlights the
     * reader left on the first.
     */
    public Optional<Bookmark> findByUrl(String url) {
        String key = UrlKey.of(url);
        return bookmarks.stream()
                .filter(bookmark -> UrlKey.of(bookmark.url()).equals(key))
                .findFirst();
    }

    public Bookmark add(LinkDraft draft) {
        findByUrl(draft.getUrl()).ifPresent(existing -> {
            throw new DuplicateBookmarkException(existing);
        });
        Bookmark bookmark = new Bookmark(
                UUID.randomUUID().toString(),
                draft.getUrl(),
                draft.getTitle(),
                draft.getSite(),
                draft.getSite(),
                draft.getDescription(),
                draft.tagsOrEmpty(),
                draft.getType(),
                draft.isReadLater(),
                clock.millis(),
                Math.max(1, draft.getReadingMinutes()),
                draft.getContent(),
                "",
                List.of());
        bookmarks.addFirst(bookmark);
        persist();
        return bookmark;
    }

    /**
     * Writes the dialog's fields back over an existing bookmark. What the reader added
     * themselves — the notes, the highlights, and when they saved it — is carried
     * across untouched: editing a title is not a reason to lose any of it.
     */
    public void update(String id, LinkDraft draft) {
        findByUrl(draft.getUrl())
                .filter(other -> !other.id().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateBookmarkException(other);
                });
        replace(id, existing -> new Bookmark(
                existing.id(),
                draft.getUrl(),
                draft.getTitle(),
                draft.getSite(),
                draft.getSite(),
                draft.getDescription(),
                draft.tagsOrEmpty(),
                draft.getType(),
                draft.isReadLater(),
                existing.savedAt(),
                Math.max(1, draft.getReadingMinutes()),
                draft.getContent(),
                existing.notes(),
                existing.highlights()));
    }

    public void toggleReadLater(String id) {
        replace(id, bookmark -> bookmark.withReadLater(!bookmark.readLater()));
    }

    public void updateNotes(String id, String notes) {
        replace(id, bookmark -> bookmark.withNotes(notes));
    }

    public void addHighlight(String id, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        replace(id, bookmark -> {
            List<Highlight> highlights = new ArrayList<>(bookmark.highlights());
            highlights.add(new Highlight(text));
            return bookmark.withHighlights(highlights);
        });
    }

    public void removeHighlight(String id, int index) {
        replace(id, bookmark -> {
            if (index < 0 || index >= bookmark.highlights().size()) {
                return bookmark;
            }
            List<Highlight> highlights = new ArrayList<>(bookmark.highlights());
            highlights.remove(index);
            return bookmark.withHighlights(highlights);
        });
    }

    public void remove(String id) {
        if (bookmarks.removeIf(bookmark -> bookmark.id().equals(id))) {
            persist();
        }
    }

    public ColorSchemePreference getColorScheme() {
        return colorScheme;
    }

    public void setColorScheme(ColorSchemePreference preference) {
        colorScheme = preference;
        persist();
    }

    private void apply(StoredLibrary library) {
        bookmarks.clear();
        bookmarks.addAll(library.bookmarks());
        colorScheme = library.colorScheme();
        loaded = true;
        notifyListeners();
    }

    private void replace(String id, UnaryOperator<Bookmark> change) {
        for (int position = 0; position < bookmarks.size(); position++) {
            if (bookmarks.get(position).id().equals(id)) {
                bookmarks.set(position, change.apply(bookmarks.get(position)));
                persist();
                return;
            }
        }
    }

    private void persist() {
        store.write(new StoredLibrary(List.copyOf(bookmarks), colorScheme));
        notifyListeners();
    }

    private void notifyListeners() {
        List.copyOf(changeListeners).forEach(Runnable::run);
    }

    private Comparator<Bookmark> comparatorFor(LibraryQuery query) {
        return switch (query.sortMode()) {
            case RECENT -> Comparator.comparingLong(Bookmark::savedAt).reversed();
            case TITLE -> Comparator.comparing(Bookmark::title, collator());
            case READING_TIME -> Comparator.comparingInt(Bookmark::readingMinutes);
        };
    }

    /**
     * ICU collation rather than {@code String.compareTo}, so "Ökonomie" sorts where
     * a reader expects it to rather than after "Zoology".
     */
    private Comparator<String> collator() {
        if (titleCollator == null) {
            titleCollator = Collator.getInstance();
        }
        return titleCollator::compare;
    }
}
