package io.binarycodes.harbor;

import java.util.function.Consumer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.binarycodes.harbor.library.service.LibraryStorage;

/**
 * Stands in for the browser's local storage in tests that have no browser. The
 * real one asks the browser and answers later, which such a test never hears back
 * from.
 */
@TestConfiguration
public class BrowserlessStorageConfiguration {

    @Bean
    @Primary
    public LibraryStorage inMemoryLibraryStorage() {
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
}
