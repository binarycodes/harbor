package io.binarycodes.harbor.library.service;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the library into JSON and back. A payload that fails to parse is treated
 * as absent rather than fatal: it is either from an older shape of the app or
 * from something else that used the same key, and neither is worth trapping the
 * reader in a broken screen over.
 */
@Component
public class BookmarkStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookmarkStore.class);

    private final LibraryStorage storage;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public BookmarkStore(LibraryStorage storage) {
        this.storage = storage;
    }

    public void read(Consumer<StoredLibrary> libraryConsumer) {
        storage.read(payload -> libraryConsumer.accept(decode(payload)));
    }

    public void write(StoredLibrary library) {
        storage.write(jsonMapper.writeValueAsString(library));
    }

    StoredLibrary decode(String payload) {
        if (payload == null || payload.isBlank()) {
            return StoredLibrary.empty();
        }
        try {
            return jsonMapper.readValue(payload, StoredLibrary.class);
        } catch (JacksonException failedToParse) {
            LOGGER.warn("Discarding an unreadable stored library", failedToParse);
            return StoredLibrary.empty();
        }
    }
}
