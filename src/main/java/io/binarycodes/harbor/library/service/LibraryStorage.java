package io.binarycodes.harbor.library.service;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 * Where the library is kept between visits. Reading is asynchronous because the
 * real implementation has to ask the browser.
 */
public interface LibraryStorage extends Serializable {

    /**
     * Hands the stored payload to the consumer, or {@code null} when nothing has
     * been stored yet.
     */
    void read(Consumer<String> payloadConsumer);

    void write(String payload);
}
