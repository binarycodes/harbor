package io.binarycodes.harbor.library.service;

import java.util.function.Consumer;

/**
 * Stands in for the browser's local storage. {@link #read} answers immediately,
 * which is the one way it differs from the real thing.
 */
class InMemoryLibraryStorage implements LibraryStorage {

    private String payload;
    private int writeCount;

    InMemoryLibraryStorage() {
        this(null);
    }

    InMemoryLibraryStorage(String initialPayload) {
        payload = initialPayload;
    }

    @Override
    public void read(Consumer<String> payloadConsumer) {
        payloadConsumer.accept(payload);
    }

    @Override
    public void write(String newPayload) {
        payload = newPayload;
        writeCount++;
    }

    String payload() {
        return payload;
    }

    int writeCount() {
        return writeCount;
    }
}
