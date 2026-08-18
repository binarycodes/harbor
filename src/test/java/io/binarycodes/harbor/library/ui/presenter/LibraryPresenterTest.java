package io.binarycodes.harbor.library.ui.presenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.shared.Registration;

import io.binarycodes.harbor.library.domain.BookmarkType;
import io.binarycodes.harbor.library.domain.LinkDraft;
import io.binarycodes.harbor.library.service.BookmarkService;
import io.binarycodes.harbor.library.service.BookmarkStore;
import io.binarycodes.harbor.library.service.LibraryStorage;

/**
 * The presenter owns telling the screens that something changed, so that is what
 * these cover. What each change does to the library itself is the service's own
 * test.
 */
@DisplayName("The library presenter")
class LibraryPresenterTest {

    private LibraryPresenter presenter;

    @BeforeEach
    void createPresenter() {
        BookmarkService bookmarkService = new BookmarkService(new BookmarkStore(answersImmediately()),
                Clock.systemUTC());
        presenter = new LibraryPresenter(bookmarkService, url -> {
            throw new UnsupportedOperationException("No test here reads a page");
        });
    }

    @Test
    @DisplayName("tells the screens once the library has arrived, and only once")
    void notifiesOnLoadOnce() {
        AtomicInteger changes = new AtomicInteger();
        presenter.addChangeListener(changes::incrementAndGet);

        presenter.load();
        presenter.load();

        assertEquals(1, changes.get());
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
     * The browser's storage answers later; this one answers on the spot, which is
     * the only way it differs.
     */
    private static LibraryStorage answersImmediately() {
        return new LibraryStorage() {

            private String payload;

            @Override
            public void read(Consumer<String> payloadConsumer) {
                payloadConsumer.accept(payload);
            }

            @Override
            public void write(String newPayload) {
                payload = newPayload;
            }
        };
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
        draft.setTags(List.of("Reading"));
        presenter.add(draft);
    }
}
