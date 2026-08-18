package io.binarycodes.harbor.library.ui.presenter;

import java.util.function.IntConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;

import io.binarycodes.harbor.base.ui.BrowserStorage;
import io.binarycodes.harbor.library.service.BookmarkService;
import io.binarycodes.harbor.library.service.LegacyLibraryDecoder;
import io.binarycodes.harbor.library.service.StoredLibrary;

/**
 * Takes in the library an older Harbor left in this browser. Before there was a
 * database the whole library lived under {@code harbor.library.v1}, and an
 * upgrade that simply started reading elsewhere would leave the reader looking at
 * an empty screen with their bookmarks still sitting there, unreferenced.
 *
 * <p>The key is cleared only once the import has been taken, so a failure halfway
 * leaves the old copy where it was rather than losing it on the way through.
 */
@Component
@VaadinSessionScope
class LegacyLibraryImport {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyLibraryImport.class);
    private static final String LEGACY_KEY = "harbor.library.v1";

    private final BrowserStorage browserStorage;
    private final LegacyLibraryDecoder decoder;
    private final BookmarkService bookmarkService;

    LegacyLibraryImport(BrowserStorage browserStorage, LegacyLibraryDecoder decoder,
            BookmarkService bookmarkService) {
        this.browserStorage = browserStorage;
        this.decoder = decoder;
        this.bookmarkService = bookmarkService;
    }

    /**
     * Reports how many bookmarks were taken in, which is zero for every browser
     * that never ran the older version — that is, almost all of them.
     */
    void run(IntConsumer whenDone) {
        browserStorage.read(LEGACY_KEY, payload -> whenDone.accept(importFrom(payload)));
    }

    private int importFrom(String payload) {
        if (payload == null || payload.isBlank()) {
            return 0;
        }
        StoredLibrary library = decoder.decode(payload);
        try {
            int imported = bookmarkService.importAll(library.bookmarks());
            browserStorage.remove(LEGACY_KEY);
            LOGGER.info("Took in {} bookmark(s) left in this browser by an older Harbor", imported);
            return imported;
        } catch (RuntimeException failed) {
            LOGGER.warn("Could not take in the library this browser had stored; leaving it where it is",
                    failed);
            return 0;
        }
    }
}
