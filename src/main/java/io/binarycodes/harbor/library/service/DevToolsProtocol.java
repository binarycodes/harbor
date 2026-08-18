package io.binarycodes.harbor.library.service;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Chrome DevTools Protocol, as far as archiving a page needs it: framing a
 * command and reading what comes back.
 *
 * <p>Kept apart from the socket that carries it because this is where the protocol
 * can be got wrong — a reply matched to the wrong command, an error read as an
 * empty result — and none of that needs a browser to test.
 *
 * <p>The protocol is asynchronous and multiplexed: every command carries an id, and
 * replies arrive in whatever order the browser finishes them, interleaved with
 * events nobody asked for. So a reply is only ever matched by its id, and anything
 * unrecognised is ignored rather than mistaken for an answer.
 */
final class DevToolsProtocol {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private DevToolsProtocol() {
    }

    /**
     * A command, addressed to a page session when there is one. The browser-level
     * commands that create and attach to a target carry no session, because there is
     * no page yet to address.
     */
    static String command(int id, String method, Map<String, Object> params, String sessionId) {
        var frame = new java.util.LinkedHashMap<String, Object>();
        frame.put("id", id);
        frame.put("method", method);
        frame.put("params", params == null ? Map.of() : params);
        if (sessionId != null && !sessionId.isBlank()) {
            frame.put("sessionId", sessionId);
        }
        return JSON.writeValueAsString(frame);
    }

    /**
     * @return the id a frame is answering, or empty when it is an event rather than a
     *         reply
     */
    static Optional<Integer> replyId(String frame) {
        JsonNode root = parse(frame);
        return root == null || !root.has("id")
                ? Optional.empty()
                : Optional.of(root.get("id").asInt());
    }

    /**
     * @return the method an event is reporting, or empty when the frame is a reply
     */
    static Optional<String> eventMethod(String frame) {
        JsonNode root = parse(frame);
        return root == null || root.has("id") || !root.has("method")
                ? Optional.empty()
                : Optional.of(root.get("method").asString());
    }

    /**
     * The browser reports a refused or impossible command as an {@code error} member
     * rather than by failing the connection, so a caller that only reads
     * {@code result} sees an absent value and cannot say why.
     */
    static Optional<String> errorMessage(String frame) {
        JsonNode root = parse(frame);
        if (root == null || !root.has("error")) {
            return Optional.empty();
        }
        JsonNode error = root.get("error");
        String message = error.path("message").asString("");
        int code = error.path("code").asInt(0);
        return Optional.of(message.isBlank() ? "DevTools error " + code : message);
    }

    static Optional<String> resultText(String frame, String field) {
        JsonNode root = parse(frame);
        if (root == null) {
            return Optional.empty();
        }
        JsonNode value = root.path("result").path(field);
        return value.isMissingNode() || value.isNull()
                ? Optional.empty()
                : Optional.of(value.asString());
    }

    /**
     * {@code Page.printToPDF} answers with the document base64-encoded in
     * {@code result.data}.
     */
    static Optional<byte[]> printedPdf(String frame) {
        return resultText(frame, "data").map(Base64.getDecoder()::decode);
    }

    /**
     * The address the browser listens on for the session that follows. Read from
     * {@code /json/version} rather than assembled by hand — the path carries an id
     * only the browser knows.
     */
    static Optional<String> webSocketDebuggerUrl(String versionJson) {
        JsonNode root = parse(versionJson);
        if (root == null) {
            return Optional.empty();
        }
        JsonNode url = root.path("webSocketDebuggerUrl");
        return url.isMissingNode() || url.asString("").isBlank()
                ? Optional.empty()
                : Optional.of(url.asString());
    }

    /**
     * A frame that is not JSON at all is treated as nothing to act on. The socket
     * hands over whatever arrived, and a malformed frame is not worth losing an
     * archive over.
     */
    private static JsonNode parse(String frame) {
        if (frame == null || frame.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(frame);
        } catch (RuntimeException notJson) {
            return null;
        }
    }
}
