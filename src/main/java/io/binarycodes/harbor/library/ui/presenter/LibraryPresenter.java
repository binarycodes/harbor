package io.binarycodes.harbor.library.ui.presenter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;

import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.ColorSchemePreference;
import io.binarycodes.harbor.library.domain.LibraryQuery;
import io.binarycodes.harbor.library.domain.LinkDraft;
import io.binarycodes.harbor.library.domain.TagCount;
import io.binarycodes.harbor.library.service.BookmarkService;
import io.binarycodes.harbor.library.service.LinkMetadata;
import io.binarycodes.harbor.library.service.MetadataResolver;

/**
 * What the screens talk to: every question they ask of the library, every change
 * they make to it, and the redraw that has to follow a change.
 *
 * <p>The change listeners live here rather than on the service because they are a
 * presentation concern — a listener is how a component learns to redraw itself,
 * and it belongs to a component in this session. Keeping them out of the service
 * leaves that free of the UI entirely, and leaves each screen with one thing to
 * depend on.
 *
 * <p>Every mutation notifies on the way out, so a screen that changes something
 * never has to remember to tell anyone.
 */
@Component
@VaadinSessionScope
public class LibraryPresenter {

    private final BookmarkService bookmarkService;
    private final MetadataResolver metadataResolver;
    private final List<Runnable> changeListeners = new ArrayList<>();

    LibraryPresenter(BookmarkService bookmarkService, MetadataResolver metadataResolver) {
        this.bookmarkService = bookmarkService;
        this.metadataResolver = metadataResolver;
    }

    public Registration addChangeListener(Runnable listener) {
        changeListeners.add(listener);
        return () -> changeListeners.remove(listener);
    }

    /**
     * Asks for the stored library, once per session. The browser answers later, so
     * screens register a listener first and are told when the contents are known.
     */
    public void load() {
        bookmarkService.load(this::notifyListeners);
    }

    public boolean isLoaded() {
        return bookmarkService.isLoaded();
    }

    public List<Bookmark> find(LibraryQuery query) {
        return bookmarkService.find(query);
    }

    public Optional<Bookmark> findById(String id) {
        return bookmarkService.findById(id);
    }

    public List<Bookmark> withHighlights() {
        return bookmarkService.withHighlights();
    }

    public int count() {
        return bookmarkService.count();
    }

    public int countReadLater() {
        return bookmarkService.countReadLater();
    }

    public int countHighlights() {
        return bookmarkService.countHighlights();
    }

    public List<TagCount> tagCounts() {
        return bookmarkService.tagCounts();
    }

    public ColorSchemePreference getColorScheme() {
        return bookmarkService.getColorScheme();
    }

    public void setColorScheme(ColorSchemePreference preference) {
        bookmarkService.setColorScheme(preference);
        notifyListeners();
    }

    public Bookmark add(LinkDraft draft) {
        Bookmark saved = bookmarkService.add(draft);
        notifyListeners();
        return saved;
    }

    public void update(String id, LinkDraft draft) {
        bookmarkService.update(id, draft);
        notifyListeners();
    }

    public void toggleReadLater(String id) {
        bookmarkService.toggleReadLater(id);
        notifyListeners();
    }

    public void updateNotes(String id, String notes) {
        bookmarkService.updateNotes(id, notes);
        notifyListeners();
    }

    public void addHighlight(String id, String text) {
        bookmarkService.addHighlight(id, text);
        notifyListeners();
    }

    public void removeHighlight(String id, int index) {
        bookmarkService.removeHighlight(id, index);
        notifyListeners();
    }

    public void remove(String id) {
        bookmarkService.remove(id);
        notifyListeners();
    }

    /**
     * Reads the page behind a URL away from the UI thread and hands the outcome
     * back through the session lock. Push is enabled for exactly this.
     *
     * <p>The failure arrives unwrapped: the caller cannot tell a refused address
     * from anything else if it has to dig through whatever the future wrapped it
     * in.
     */
    public void resolve(UI ui, String url, Consumer<LinkMetadata> onResolved,
            Consumer<Throwable> onFailed) {
        CompletableFuture
                .supplyAsync(() -> metadataResolver.resolve(url))
                .whenComplete((metadata, failure) -> ui.access(() -> {
                    if (metadata != null) {
                        onResolved.accept(metadata);
                        return;
                    }
                    onFailed.accept(failure instanceof CompletionException ? failure.getCause() : failure);
                }));
    }

    private void notifyListeners() {
        List.copyOf(changeListeners).forEach(Runnable::run);
    }
}
