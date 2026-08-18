package io.binarycodes.harbor.base.ui;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 * The browser's own storage. Only two things belong here now that the library is
 * in the database: what this browser prefers to look like, and whatever an older
 * version of Harbor left behind.
 *
 * <p>Reading is asynchronous because the answer has to come back from the
 * browser.
 */
public interface BrowserStorage extends Serializable {

    /**
     * Hands the stored value to the consumer, or {@code null} when the key holds
     * nothing.
     */
    void read(String key, Consumer<String> valueConsumer);

    void write(String key, String value);

    void remove(String key);
}
