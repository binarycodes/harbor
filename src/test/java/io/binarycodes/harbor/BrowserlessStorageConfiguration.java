package io.binarycodes.harbor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.binarycodes.harbor.base.ui.BrowserStorage;

/**
 * Stands in for the browser's own storage in tests that have no browser. The real
 * one asks the browser and answers later, which such a test never hears back
 * from.
 */
@TestConfiguration
public class BrowserlessStorageConfiguration {

    @Bean
    @Primary
    public BrowserStorage inMemoryBrowserStorage() {
        return new BrowserStorage() {

            private final Map<String, String> values = new HashMap<>();

            @Override
            public void read(String key, Consumer<String> valueConsumer) {
                valueConsumer.accept(values.get(key));
            }

            @Override
            public void write(String key, String value) {
                values.put(key, value);
            }

            @Override
            public void remove(String key) {
                values.remove(key);
            }
        };
    }
}
