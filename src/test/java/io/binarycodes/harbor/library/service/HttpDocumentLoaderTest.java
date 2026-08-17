package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Exercises the loader against a real server on loopback. Loopback is blocked by
 * default, so these tests permit it explicitly — which is also what proves the
 * escape hatch works end to end.
 */
@DisplayName("Fetching a page through the guarded client")
class HttpDocumentLoaderTest {

    private static final String PAGE = """
            <html><head><title>A real page</title></head>
            <body><article><p>Words.</p></article></body></html>
            """;

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("parses what the server sent")
    void parsesTheResponse() throws IOException {
        serve("/article", exchange -> respond(exchange, 200, "text/html; charset=utf-8", PAGE));

        Document document = loaderPermittingLoopback().load(url("/article"));

        assertEquals("A real page", document.title());
    }

    /**
     * The reason the check lives in the DNS resolver: a public host may answer with a
     * redirect, and the hop is a fresh connection that has to be vetted too.
     */
    @Test
    @DisplayName("refuses a redirect into an address it would not have fetched directly")
    void refusesRedirectIntoBlockedSpace() throws IOException {
        serve("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        HttpDocumentLoader loader = loaderPermittingLoopback();

        assertThrows(BlockedAddressException.class, () -> loader.load(url("/redirect")));
    }

    @Test
    @DisplayName("refuses an address outside the permitted ranges outright")
    void refusesBlockedAddressDirectly() {
        HttpDocumentLoader loader = loader(List.of());

        assertThrows(BlockedAddressException.class, () -> loader.load(url("/article")));
    }

    @Test
    @DisplayName("refuses a response that is not a page")
    void refusesNonHtml() throws IOException {
        serve("/data", exchange -> respond(exchange, 200, "application/json", "{\"secret\":true}"));

        HttpDocumentLoader loader = loaderPermittingLoopback();

        IOException failure = assertThrows(IOException.class, () -> loader.load(url("/data")));
        assertTrue(failure.getMessage().contains("application/json"));
    }

    @Test
    @DisplayName("treats an error status as a page it could not read")
    void refusesErrorStatus() throws IOException {
        serve("/missing", exchange -> respond(exchange, 404, "text/html", "<html>gone</html>"));

        HttpDocumentLoader loader = loaderPermittingLoopback();

        assertThrows(IOException.class, () -> loader.load(url("/missing")));
    }

    @Test
    @DisplayName("stops reading an oversized body instead of taking all of it")
    void truncatesLongBodies() throws IOException {
        String padding = "x".repeat(50_000);
        serve("/huge", exchange -> respond(exchange, 200, "text/html",
                "<html><body><p>" + padding + "</p></body></html>"));

        Document document = loader(List.of("127.0.0.0/8"), 1024).load(url("/huge"));

        assertTrue(document.text().length() < padding.length());
    }

    private void serve(String path, UnsafeHandler handler) {
        server.createContext(path, exchange -> {
            try {
                handler.handle(exchange);
            } catch (IOException problem) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        });
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static HttpDocumentLoader loaderPermittingLoopback() {
        return loader(List.of("127.0.0.0/8"));
    }

    private static HttpDocumentLoader loader(List<String> allowed) {
        return loader(allowed, 512 * 1024);
    }

    private static HttpDocumentLoader loader(List<String> allowed, int maxBodyBytes) {
        OutboundFetchProperties properties = new OutboundFetchProperties(
                ReservedAddressRanges.notations(), allowed, Duration.ofSeconds(5), 5, maxBodyBytes);
        return new HttpDocumentLoader(
                new GuardedDnsResolver(new OutboundAddressPolicy(properties)), properties);
    }

    private interface UnsafeHandler {

        void handle(HttpExchange exchange) throws IOException;
    }
}
