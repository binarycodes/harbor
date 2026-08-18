package io.binarycodes.harbor.library.ui.presenter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;

import io.binarycodes.harbor.library.domain.LibraryQuery;
import io.binarycodes.harbor.library.domain.LibraryScope;
import io.binarycodes.harbor.library.domain.SortMode;
import io.binarycodes.harbor.library.domain.ViewMode;

/**
 * How the reader is currently looking at their library: which tags they picked,
 * what they typed in the search box, and the order and density they prefer. The
 * sidebar and the listing sit in different parts of the component tree, so this
 * is what they agree through. It lasts for the session and is deliberately not
 * persisted — a filter is where you are right now, not a setting.
 */
@Component
@VaadinSessionScope
public class LibraryFilter {

    private final Set<String> selectedTags = new LinkedHashSet<>();
    private final List<Runnable> changeListeners = new ArrayList<>();

    private String searchText = "";
    private SortMode sortMode = SortMode.RECENT;
    private ViewMode viewMode = ViewMode.CARDS;

    public Registration addChangeListener(Runnable listener) {
        changeListeners.add(listener);
        return () -> changeListeners.remove(listener);
    }

    public LibraryQuery query(LibraryScope scope) {
        return new LibraryQuery(scope, selectedTags, searchText, sortMode);
    }

    public Set<String> getSelectedTags() {
        return Set.copyOf(selectedTags);
    }

    public boolean isSelected(String tag) {
        return selectedTags.contains(tag);
    }

    public boolean hasSelectedTags() {
        return !selectedTags.isEmpty();
    }

    public void toggleTag(String tag) {
        if (!selectedTags.remove(tag)) {
            selectedTags.add(tag);
        }
        notifyListeners();
    }

    public void clearTags() {
        if (selectedTags.isEmpty()) {
            return;
        }
        selectedTags.clear();
        notifyListeners();
    }

    /**
     * Drops tags that no longer exist, which happens when the last bookmark
     * carrying one is deleted while it is still selected.
     */
    public void retainTags(Set<String> knownTags) {
        if (selectedTags.retainAll(knownTags)) {
            notifyListeners();
        }
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String value) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.equals(searchText)) {
            return;
        }
        searchText = cleaned;
        notifyListeners();
    }

    public SortMode getSortMode() {
        return sortMode;
    }

    public void setSortMode(SortMode value) {
        if (value == sortMode) {
            return;
        }
        sortMode = value;
        notifyListeners();
    }

    public ViewMode getViewMode() {
        return viewMode;
    }

    public void setViewMode(ViewMode value) {
        if (value == viewMode) {
            return;
        }
        viewMode = value;
        notifyListeners();
    }

    private void notifyListeners() {
        List.copyOf(changeListeners).forEach(Runnable::run);
    }
}
