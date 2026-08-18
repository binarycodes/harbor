package io.binarycodes.harbor.library.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One conversation with the browser over its DevTools socket.
 *
 * <p>Deliberately thin: it carries frames, matches replies to the commands that
 * asked for them, and waits for events. Every decision about what a frame means
 * belongs to {@link DevToolsProtocol}, which needs no browser to test.
 *
 * <p>The protocol is multiplexed, so a reply is only ever matched by id and an
 * unrecognised frame is dropped. Frames can also arrive split across several
 * messages, which is why text is accumulated until the socket says it is complete.
 */
final class DevToolsSession implements AutoCloseable {

    private final WebSocket socket;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, CompletableFuture<String>> awaitingReply = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<String>> awaitingEvent = new ConcurrentHashMap<>();

    private DevToolsSession(WebSocket socket) {
        this.socket = socket;
    }

    static DevToolsSession open(HttpClient httpClient, String socketUrl, Duration timeout)
            throws Exception {
        Listener listener = new Listener();
        WebSocket socket = httpClient.newWebSocketBuilder()
                .connectTimeout(timeout)
                .buildAsync(URI.create(socketUrl), listener)
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        DevToolsSession session = new DevToolsSession(socket);
        listener.deliverTo(session);
        return session;
    }

    /**
     * Sends a command and waits for the reply carrying its id.
     *
     * @throws DevToolsException when the browser refuses the command, or does not
     *                          answer inside the deadline
     */
    String call(String method, Map<String, Object> params, String sessionId, Duration timeout) {
        int id = nextId.getAndIncrement();
        CompletableFuture<String> reply = new CompletableFuture<>();
        awaitingReply.put(id, reply);
        try {
            socket.sendText(DevToolsProtocol.command(id, method, params, sessionId), true)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            String frame = reply.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            Optional<String> failure = DevToolsProtocol.errorMessage(frame);
            if (failure.isPresent()) {
                throw new DevToolsException(method + ": " + failure.get());
            }
            return frame;
        } catch (TimeoutException tooSlow) {
            throw new DevToolsException(method + " did not answer within " + timeout);
        } catch (DevToolsException refused) {
            throw refused;
        } catch (Exception failed) {
            throw new DevToolsException(method + " failed: " + failed.getMessage());
        } finally {
            awaitingReply.remove(id);
        }
    }

    /**
     * Waits for an event the browser raises of its own accord — a page finishing
     * loading, for one. Registered before the command that provokes it, or the event
     * can arrive first and be dropped.
     */
    CompletableFuture<String> expect(String eventMethod) {
        return awaitingEvent.computeIfAbsent(eventMethod, ignored -> new CompletableFuture<>());
    }

    void await(CompletableFuture<String> event, String what, Duration timeout) {
        try {
            event.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException tooSlow) {
            throw new DevToolsException(what + " did not arrive within " + timeout);
        } catch (Exception failed) {
            throw new DevToolsException(what + " failed: " + failed.getMessage());
        }
    }

    private void deliver(String frame) {
        DevToolsProtocol.replyId(frame)
                .map(awaitingReply::get)
                .ifPresent(waiting -> waiting.complete(frame));
        DevToolsProtocol.eventMethod(frame)
                .map(awaitingEvent::get)
                .ifPresent(waiting -> waiting.complete(frame));
    }

    @Override
    public void close() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        socket.abort();
    }

    /**
     * Accumulates the parts of a frame until the socket says it is whole, then hands
     * it over. A large {@code printToPDF} reply arrives in many parts, and treating
     * each as a frame would leave every one of them unparseable.
     */
    private static final class Listener implements WebSocket.Listener {

        private final StringBuilder partial = new StringBuilder();
        private volatile DevToolsSession session;

        void deliverTo(DevToolsSession target) {
            this.session = target;
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String frame = partial.toString();
                partial.setLength(0);
                DevToolsSession waiting = session;
                if (waiting != null) {
                    waiting.deliver(frame);
                }
            }
            socket.request(1);
            return null;
        }

        /**
         * A socket that drops mid-archive must not leave a caller waiting out its whole
         * deadline, so everything still expecting an answer is failed at once.
         */
        @Override
        public void onError(WebSocket socket, Throwable error) {
            failEverything(error.getMessage());
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
            failEverything("the browser closed the connection: " + reason);
            return null;
        }

        private void failEverything(String why) {
            DevToolsSession waiting = session;
            if (waiting == null) {
                return;
            }
            DevToolsException failure = new DevToolsException(why);
            waiting.awaitingReply.values().forEach(reply -> reply.completeExceptionally(failure));
            waiting.awaitingEvent.values().forEach(event -> event.completeExceptionally(failure));
        }
    }

    static final class DevToolsException extends RuntimeException {

        DevToolsException(String message) {
            super(message);
        }
    }
}
