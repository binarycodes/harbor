package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import io.binarycodes.harbor.HarborDatabase;
import io.binarycodes.harbor.StubIdentityConfiguration;
import io.binarycodes.harbor.StubMetadataConfiguration;
import io.binarycodes.harbor.library.domain.ArchiveStatus;
import io.binarycodes.harbor.library.domain.Bookmark;
import io.binarycodes.harbor.library.domain.BookmarkType;
import io.binarycodes.harbor.library.domain.LinkDraft;

/**
 * The other archiving mode: the bookmark is filed as soon as the page has been read,
 * and its copy of the page follows on a background thread. What the same drafts do
 * under the shipped default is {@link BookmarkServiceTest}.
 *
 * <p>Its own class rather than a nested one, because the mode is a property and a
 * property needs its own context. The archiver is stubbed and made to wait on command,
 * which is the only way the intermediate state is observable at all — a real render
 * would either be too fast to catch or too slow to wait for.
 *
 * <p>This is also the tier that proves the owner survives the hop. The render finishes
 * on a thread with nobody signed in to it, so an archiver that reached for
 * {@link LibraryOwner} rather than carrying the owner it was given would fail here and
 * nowhere else.
 */
@SpringBootTest(properties = "harbor.archive.force-before-save=false")
@Import({ HarborDatabase.class, StubIdentityConfiguration.class,
        BackgroundArchiveTest.HeldArchiverConfiguration.class })
@DisplayName("When the archive is rendered after the save")
@ActiveProfiles("test")
class BackgroundArchiveTest {

    /**
     * Long enough that a loaded machine does not fail a test that would otherwise have
     * passed, short enough that a genuinely stuck render is reported rather than waited
     * out.
     */
    private static final Duration PATIENCE = Duration.ofSeconds(10);

    @Autowired
    private BookmarkService service;

    @Autowired
    private BookmarkArchiveService archives;

    @Autowired
    private BookmarkRepository repository;

    @Autowired
    private PendingArchiveRecovery recovery;

    @Autowired
    private StubIdentityConfiguration identity;

    @Autowired
    private HeldArchiver archiver;

    @BeforeEach
    void startFromAnEmptyLibrary() {
        repository.deleteAll();
        identity.actAs(StubIdentityConfiguration.READER);
        archiver.expect(Optional.of(StubMetadataConfiguration.STUB_ARCHIVE));
    }

    /**
     * There is one render thread and it is shared by every test here, so a test that
     * leaves one held would stall the next. Draining also keeps a render from landing
     * on top of the row the next test is about to delete.
     */
    @AfterEach
    void letEveryRenderFinish() {
        archiver.release();
        awaitUntil(() -> repository.findPendingArchives().isEmpty(),
                "renders were still queued");
    }

    @Test
    @DisplayName("the bookmark is saved before there is anything to archive against it")
    void filesTheBookmarkFirst() {
        archiver.hold();

        Bookmark saved = service.add(draft("https://example.com/one", "One"));

        assertEquals("One", saved.title());
        assertEquals(ArchiveStatus.PENDING, statusOf(saved.id()));
        assertFalse(archives.exists(saved.id()));
    }

    @Test
    @DisplayName("the archive lands afterwards, against the reader whose library it is")
    void archivesInTheBackground() {
        Bookmark saved = service.add(draft("https://example.com/one", "One"));

        awaitStatus(saved.id(), ArchiveStatus.READY);

        assertTrue(archives.exists(saved.id()));
        assertArrayEquals(StubMetadataConfiguration.STUB_ARCHIVE,
                archives.findBytes(saved.id()).orElseThrow());
    }

    /**
     * The save gate is the dialog's, and it is off in this mode — but the service is
     * where it has to be right, since it is the caller that cannot be bypassed.
     */
    @Test
    @DisplayName("a draft with no archive is accepted rather than refused")
    void acceptsADraftWithNoArchive() {
        LinkDraft draft = draft("https://example.com/one", "One");
        draft.setArchive(null);

        String id = service.add(draft).id();

        assertEquals(1, service.count());
        awaitStatus(id, ArchiveStatus.READY);
    }

    /**
     * A page that will not render has to stop saying it is being worked on. Left
     * PENDING it would be a reader waiting on something that has already given up.
     */
    @Test
    @DisplayName("a page that will not render ends up FAILED rather than waiting for ever")
    void reportsAFailedRender() {
        archiver.expect(Optional.empty());

        Bookmark saved = service.add(draft("https://example.com/unrenderable", "Unrenderable"));

        awaitStatus(saved.id(), ArchiveStatus.FAILED);
        assertFalse(archives.exists(saved.id()));
    }

    /**
     * An edit that re-read the page is owed a fresh copy of it; one that only corrected
     * a title is owed nothing, and must keep the copy it has.
     */
    @Test
    @DisplayName("re-reading a page queues a fresh archive, correcting a title does not")
    void reArchivesOnlyAfterAReRead() {
        String id = service.add(draft("https://example.com/one", "One")).id();
        awaitStatus(id, ArchiveStatus.READY);

        service.update(id, draft("https://example.com/one", "One, corrected"));
        assertEquals(ArchiveStatus.READY, statusOf(id));

        LinkDraft reRead = draft("https://example.com/one", "One, re-read");
        reRead.setRefetched(true);
        archiver.hold();
        service.update(id, reRead);

        assertEquals(ArchiveStatus.PENDING, statusOf(id));
        archiver.release();
        awaitStatus(id, ArchiveStatus.READY);
    }

    /**
     * A render abandoned by a restart is the one failure the queue cannot report on its
     * own, because the queue went with it. The row is left as a restart would leave it
     * rather than by stopping a thread, which is the state that matters and the only
     * part a test can honestly reproduce.
     */
    @Test
    @DisplayName("an archive left unfinished by a restart is picked up again")
    void resumesAfterARestart() {
        String id = service.add(draft("https://example.com/one", "One")).id();
        awaitStatus(id, ArchiveStatus.READY);
        archives.markStatus(id, StubIdentityConfiguration.READER, ArchiveStatus.PENDING);

        recovery.resumeInterruptedRenders();

        awaitStatus(id, ArchiveStatus.READY);
        assertTrue(archives.exists(id));
    }

    private ArchiveStatus statusOf(String id) {
        return service.findById(id).orElseThrow().archiveStatus();
    }

    private void awaitStatus(String id, ArchiveStatus expected) {
        awaitUntil(() -> statusOf(id) == expected, "the archive never reached " + expected);
        assertEquals(expected, statusOf(id));
    }

    private static void awaitUntil(BooleanSupplier settled, String complaint) {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        while (System.nanoTime() < deadline) {
            if (settled.getAsBoolean()) {
                return;
            }
            sleepBriefly();
        }
        assertTrue(settled.getAsBoolean(), complaint);
    }

    private static void sleepBriefly() {
        try {
            TimeUnit.MILLISECONDS.sleep(25);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static LinkDraft draft(String url, String title) {
        LinkDraft draft = new LinkDraft();
        draft.setUrl(url);
        draft.setTitle(title);
        draft.setSite("example.com");
        draft.setDescription("Saved from example.com");
        draft.setType(BookmarkType.ARTICLE);
        draft.setReadingMinutes(7);
        draft.setContent("## Body\n\nSome words.");
        return draft;
    }

    /**
     * An archiver a test can stop mid-render, so that PENDING is something it can look
     * at rather than something it has to trust.
     */
    static class HeldArchiver implements ArticleArchiver {

        private final AtomicReference<CountDownLatch> gate =
                new AtomicReference<>(new CountDownLatch(0));
        private final AtomicReference<Optional<byte[]>> outcome =
                new AtomicReference<>(Optional.empty());

        @Override
        public Optional<byte[]> archive(String title, String url, long archivedAt) {
            try {
                if (!gate.get().await(PATIENCE.toMillis(), TimeUnit.MILLISECONDS)) {
                    return Optional.empty();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            return outcome.get();
        }

        void expect(Optional<byte[]> archive) {
            gate.set(new CountDownLatch(0));
            outcome.set(archive);
        }

        void hold() {
            gate.set(new CountDownLatch(1));
        }

        /**
         * Releases whichever latch a render is actually waiting on, which is not
         * necessarily the current one — {@link #expect} installs a fresh open latch and
         * a thread already blocked stays on the old one.
         */
        void release() {
            gate.getAndSet(new CountDownLatch(0)).countDown();
        }
    }

    @TestConfiguration
    static class HeldArchiverConfiguration {

        @Bean
        @Primary
        HeldArchiver heldArchiver() {
            return new HeldArchiver();
        }
    }
}
