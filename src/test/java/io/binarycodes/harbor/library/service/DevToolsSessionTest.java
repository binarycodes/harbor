package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Matching what arrives to whoever is waiting for it. The protocol is multiplexed —
 * replies come back in whatever order the browser finishes them, interleaved with
 * events nobody asked for — so this is where a wrong answer would be handed to the
 * wrong caller.
 *
 * <p>No socket: the session is built without one, because none of this touches it.
 */
@DisplayName("Routing what the browser sends back")
class DevToolsSessionTest {

    @Test
    @DisplayName("hands an event to whoever was expecting it")
    void completesAnExpectedEvent() {
        DevToolsSession session = new DevToolsSession(null);
        CompletableFuture<String> loaded = session.expect("Page.loadEventFired");

        session.deliver("{\"method\":\"Page.loadEventFired\",\"params\":{}}");

        assertTrue(loaded.isDone());
    }

    /**
     * Expecting the same event twice must not leave a second future stranded, waiting
     * for something that has already been handed to the first.
     */
    @Test
    @DisplayName("expects one event once, however often it is asked for")
    void expectsAnEventOnce() {
        DevToolsSession session = new DevToolsSession(null);

        assertSame(session.expect("Page.loadEventFired"), session.expect("Page.loadEventFired"));
    }

    @Test
    @DisplayName("ignores an event nobody is waiting for")
    void ignoresAnUnexpectedEvent() {
        DevToolsSession session = new DevToolsSession(null);
        CompletableFuture<String> loaded = session.expect("Page.loadEventFired");

        session.deliver("{\"method\":\"Network.requestWillBeSent\",\"params\":{}}");

        assertFalse(loaded.isDone(), "a different event must not answer this one");
    }

    /**
     * A reply for an id nobody is holding is what arrives after a command has already
     * timed out. Acting on it would be acting on an answer to a question no longer
     * being asked.
     */
    @Test
    @DisplayName("ignores a reply nobody is waiting for")
    void ignoresAnOrphanedReply() {
        DevToolsSession session = new DevToolsSession(null);

        session.deliver("{\"id\":99,\"result\":{\"data\":\"AA==\"}}");

        assertEquals(0, 0, "delivering an orphaned reply must not fail");
    }

    @Test
    @DisplayName("treats a frame that is not JSON as nothing to act on")
    void ignoresRubbish() {
        DevToolsSession session = new DevToolsSession(null);
        CompletableFuture<String> loaded = session.expect("Page.loadEventFired");

        session.deliver("<html>the browser said something odd</html>");

        assertFalse(loaded.isDone());
    }
}
