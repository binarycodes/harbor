package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The protocol, without a browser. Everything that can be got wrong about talking
 * to Chromium — matching a reply to the command that asked for it, telling an error
 * from an empty answer, ignoring the events nobody asked for — is decided here, so
 * it can be pinned down without one.
 */
@DisplayName("Talking the DevTools protocol")
class DevToolsProtocolTest {

    @Nested
    @DisplayName("framing a command")
    class Framing {

        @Test
        @DisplayName("carries the id, the method and the parameters")
        void framesACommand() {
            String frame = DevToolsProtocol.command(7, "Page.navigate",
                    Map.of("url", "https://example.com/one"), "abc123");

            assertTrue(frame.contains("\"id\":7"));
            assertTrue(frame.contains("\"method\":\"Page.navigate\""));
            assertTrue(frame.contains("https://example.com/one"));
            assertTrue(frame.contains("\"sessionId\":\"abc123\""));
        }

        /**
         * The commands that create and attach to a target are addressed to the browser
         * itself. Sending an empty sessionId with them is not the same as sending none.
         */
        @Test
        @DisplayName("leaves the session out when there is not one yet")
        void omitsAnAbsentSession() {
            assertFalse(DevToolsProtocol.command(1, "Target.createTarget",
                    Map.of("url", "about:blank"), null).contains("sessionId"));
            assertFalse(DevToolsProtocol.command(1, "Target.createTarget",
                    Map.of("url", "about:blank"), "  ").contains("sessionId"));
        }

        @Test
        @DisplayName("sends an empty parameter object rather than none")
        void framesMissingParameters() {
            assertTrue(DevToolsProtocol.command(2, "Page.enable", null, "s").contains("\"params\":{}"));
        }
    }

    @Nested
    @DisplayName("matching a reply to the command that asked for it")
    class Correlation {

        @Test
        @DisplayName("reads the id a reply is answering")
        void readsAReplyId() {
            assertEquals(4, DevToolsProtocol.replyId("{\"id\":4,\"result\":{}}").orElseThrow());
        }

        /**
         * Events arrive unbidden and interleaved with replies. Treating one as an answer
         * would resolve whatever command happened to be waiting.
         */
        @Test
        @DisplayName("does not mistake an event for a reply")
        void ignoresEvents() {
            String event = "{\"method\":\"Page.loadEventFired\",\"params\":{\"timestamp\":1}}";

            assertTrue(DevToolsProtocol.replyId(event).isEmpty());
            assertEquals("Page.loadEventFired", DevToolsProtocol.eventMethod(event).orElseThrow());
        }

        @Test
        @DisplayName("does not mistake a reply for an event")
        void ignoresRepliesWhenLookingForEvents() {
            assertTrue(DevToolsProtocol.eventMethod("{\"id\":9,\"result\":{}}").isEmpty());
        }

        /**
         * A frame that is not JSON is nothing to act on. Losing an archive because the
         * browser said something unexpected would be the wrong trade.
         */
        @Test
        @DisplayName("treats an unreadable frame as nothing to act on")
        void toleratesRubbish() {
            assertTrue(DevToolsProtocol.replyId("not json at all").isEmpty());
            assertTrue(DevToolsProtocol.eventMethod("").isEmpty());
            assertTrue(DevToolsProtocol.errorMessage(null).isEmpty());
            assertTrue(DevToolsProtocol.printedPdf("{{{").isEmpty());
        }
    }

    @Nested
    @DisplayName("reading what came back")
    class Results {

        @Test
        @DisplayName("decodes the printed document")
        void decodesThePdf() {
            byte[] pdf = "%PDF-1.4 tiny".getBytes(StandardCharsets.UTF_8);
            String frame = "{\"id\":1,\"result\":{\"data\":\""
                    + Base64.getEncoder().encodeToString(pdf) + "\"}}";

            assertEquals("%PDF-1.4 tiny",
                    new String(DevToolsProtocol.printedPdf(frame).orElseThrow(), StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("reads a named result field")
        void readsAResultField() {
            String frame = "{\"id\":1,\"result\":{\"sessionId\":\"S-42\"}}";

            assertEquals("S-42", DevToolsProtocol.resultText(frame, "sessionId").orElseThrow());
        }

        @Test
        @DisplayName("reports nothing for a field the reply does not carry")
        void readsAnAbsentField() {
            assertTrue(DevToolsProtocol.resultText("{\"id\":1,\"result\":{}}", "sessionId").isEmpty());
        }

        /**
         * The browser reports a refused command as an error member rather than by
         * failing the connection. A caller reading only {@code result} would see an
         * absent value and be unable to say why — which is how an archive silently
         * becomes empty bytes.
         */
        @Test
        @DisplayName("surfaces an error rather than letting it read as an empty answer")
        void surfacesAnError() {
            String frame = "{\"id\":1,\"error\":{\"code\":-32000,"
                    + "\"message\":\"Printing is not available\"}}";

            assertEquals("Printing is not available",
                    DevToolsProtocol.errorMessage(frame).orElseThrow());
            assertTrue(DevToolsProtocol.printedPdf(frame).isEmpty());
        }

        @Test
        @DisplayName("names the code when an error carries no message")
        void surfacesACodeOnlyError() {
            assertEquals("DevTools error -32601",
                    DevToolsProtocol.errorMessage("{\"id\":1,\"error\":{\"code\":-32601}}").orElseThrow());
        }

        @Test
        @DisplayName("finds no error in a successful reply")
        void findsNoErrorInSuccess() {
            assertTrue(DevToolsProtocol.errorMessage("{\"id\":1,\"result\":{\"data\":\"AA==\"}}").isEmpty());
        }
    }

    @Nested
    @DisplayName("finding the browser's socket")
    class Endpoint {

        /**
         * The path carries an id only the browser knows, so it is read from
         * /json/version rather than assembled.
         */
        @Test
        @DisplayName("reads the socket address the browser reports")
        void readsTheSocketUrl() {
            String version = "{\"Browser\":\"HeadlessChrome/148\","
                    + "\"webSocketDebuggerUrl\":\"ws://sidecar:9222/devtools/browser/9f2c\"}";

            assertEquals("ws://sidecar:9222/devtools/browser/9f2c",
                    DevToolsProtocol.webSocketDebuggerUrl(version).orElseThrow());
        }

        @Test
        @DisplayName("reports nothing when the browser did not offer one")
        void readsAnAbsentSocketUrl() {
            assertTrue(DevToolsProtocol.webSocketDebuggerUrl("{\"Browser\":\"x\"}").isEmpty());
            assertTrue(DevToolsProtocol.webSocketDebuggerUrl(
                    "{\"webSocketDebuggerUrl\":\"\"}").isEmpty());
            assertTrue(DevToolsProtocol.webSocketDebuggerUrl("<html>not devtools</html>").isEmpty());
        }
    }
}
