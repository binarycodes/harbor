package io.binarycodes.harbor.library.service;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.binarycodes.harbor.library.domain.ArchiveStatus;
import jakarta.annotation.PreDestroy;

/**
 * Renders archives after their bookmarks have been saved, for the deployment that
 * would rather file a link at once than wait for a headless browser. Every request
 * ends in a status the reader can see: {@link ArchiveStatus#READY} with bytes behind
 * it, or {@link ArchiveStatus#FAILED}.
 *
 * <p>One thread. There is one browser to render in, so a second thread would only
 * move the queue from here into the sidecar, and a render is the most expensive thing
 * Harbor does — a burst of saves is better served slowly than by a browser thrashing.
 *
 * <p>Nothing here is durable, and it does not need to be: work is queued only against
 * a bookmark already committed as {@link ArchiveStatus#PENDING}, so anything this
 * loses to a shutdown is still on the bookmark row for
 * {@link PendingArchiveRecovery} to pick up. That is also why the shutdown below
 * does not wait — an abandoned render costs a re-render, and holding a deployment's
 * restart open for a page nobody is watching costs more.
 */
@Component
class BackgroundArchiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundArchiver.class);

    private final ArticleArchiver archiver;
    private final BookmarkArchiveService archives;
    private final Clock clock;
    private final ExecutorService renders;

    BackgroundArchiver(ArticleArchiver archiver, BookmarkArchiveService archives, Clock clock) {
        this.archiver = archiver;
        this.archives = archives;
        this.clock = clock;
        this.renders = Executors.newSingleThreadExecutor(namedDaemon());
    }

    /**
     * Queues a render. Never throws: the bookmark is already saved by the time this
     * is called, and a queue that will not take the work is not a reason to undo it.
     */
    void submit(ArchiveRequest request) {
        try {
            renders.execute(() -> render(request));
        } catch (RuntimeException refused) {
            LOGGER.warn("Could not queue an archive of {}: {}", request.url(), refused.getMessage());
            markFailed(request);
        }
    }

    @PreDestroy
    void stop() {
        renders.shutdownNow();
    }

    private void render(ArchiveRequest request) {
        try {
            Optional<byte[]> archive = archiver.archive(request.title(), request.url(), clock.millis());
            if (archive.isEmpty()) {
                LOGGER.info("Nothing to archive for {}", request.url());
                markFailed(request);
                return;
            }
            archives.store(request.bookmarkId(), request.ownerId(), archive.get(), clock.millis());
            archives.markStatus(request.bookmarkId(), request.ownerId(), ArchiveStatus.READY);
        } catch (RuntimeException failed) {
            // Nothing above this catches: the thread belongs to the executor, and an
            // escaping exception would leave the bookmark PENDING for a render that
            // is no longer coming.
            LOGGER.warn("Failed to archive {}", request.url(), failed);
            markFailed(request);
        }
    }

    /**
     * A bookmark deleted while its archive was rendering has no row left to mark, and
     * the update quietly moves nothing. That is the intended outcome, not a case to
     * report.
     */
    private void markFailed(ArchiveRequest request) {
        try {
            archives.markStatus(request.bookmarkId(), request.ownerId(), ArchiveStatus.FAILED);
        } catch (RuntimeException unreachable) {
            LOGGER.warn("Could not record a failed archive of {}: {}", request.url(),
                    unreachable.getMessage());
        }
    }

    private static ThreadFactory namedDaemon() {
        return runnable -> {
            Thread thread = new Thread(runnable, "harbor-archiver");
            thread.setDaemon(true);
            return thread;
        };
    }
}
