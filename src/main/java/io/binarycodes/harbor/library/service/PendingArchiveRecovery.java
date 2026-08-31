package io.binarycodes.harbor.library.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Picks up the archives that were still rendering when the application last stopped.
 * Without this a bookmark saved a moment before a restart would stay
 * {@code PENDING} for good — a reader waiting on a render that no longer exists,
 * which is a worse outcome than the failure it would otherwise have been told about.
 *
 * <p>It runs whatever the archiving mode is. A bookmark that was filed without its
 * archive is owed one either way, and turning
 * {@code harbor.archive.force-before-save} back on is not a reason to abandon the
 * ones already waiting.
 */
@Component
class PendingArchiveRecovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingArchiveRecovery.class);

    private final BookmarkRepository bookmarks;
    private final BackgroundArchiver archiver;

    PendingArchiveRecovery(BookmarkRepository bookmarks, BackgroundArchiver archiver) {
        this.bookmarks = bookmarks;
        this.archiver = archiver;
    }

    @EventListener(ApplicationReadyEvent.class)
    void resumeInterruptedRenders() {
        List<PendingArchiveRow> waiting = bookmarks.findPendingArchives();
        if (waiting.isEmpty()) {
            return;
        }
        LOGGER.info("Resuming {} archive(s) left unfinished", waiting.size());
        waiting.forEach(row -> archiver.submit(
                new ArchiveRequest(row.getId(), row.getOwnerId(), row.getUrl(), row.getTitle())));
    }
}
