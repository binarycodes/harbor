package io.binarycodes.harbor.library.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the JSON that older versions of Harbor kept in the browser, so that a
 * library saved before there was a database can be taken in rather than
 * abandoned.
 *
 * <p>A payload that fails to parse is treated as absent rather than fatal: it is
 * either from a shape of the app that no longer exists or from something else
 * that used the same key, and neither is worth trapping the reader in a broken
 * screen over.
 */
@Component
public class LegacyLibraryDecoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyLibraryDecoder.class);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public StoredLibrary decode(String payload) {
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
