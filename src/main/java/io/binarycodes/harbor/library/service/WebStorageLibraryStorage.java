package io.binarycodes.harbor.library.service;

import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.vaadin.flow.component.page.WebStorage;

/**
 * Keeps the library in the browser's local storage, which is what makes Harbor
 * local-first: the server never holds a copy. The key is versioned because a
 * change to the stored shape has no migration path other than starting over.
 */
@Component
public class WebStorageLibraryStorage implements LibraryStorage {

    private static final String STORAGE_KEY = "harbor.library.v1";

    @Override
    public void read(Consumer<String> payloadConsumer) {
        WebStorage.getItem(STORAGE_KEY, payloadConsumer::accept);
    }

    @Override
    public void write(String payload) {
        WebStorage.setItem(STORAGE_KEY, payload);
    }
}
