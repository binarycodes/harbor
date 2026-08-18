package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.binarycodes.harbor.ArchivingBrowser;

/**
 * Archiving through a real browser. The page comes from a server on loopback, so
 * what is being tested is the protocol conversation rather than anyone's website.
 *
 * <p>The browser comes from {@link ArchivingBrowser} — a container, or one of your
 * own if you pointed the build at it. These never skip: archiving is the only way
 * Harbor makes a PDF, so a suite that quietly passed without exercising it would be
 * covering nothing.
 */
@DisplayName("Archiving a page with the browser")
class BrowserPageArchiverTest {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/plain", exchange -> respond(exchange, """
                <html><head><title>Plain</title></head>
                <body><h1>A plain page</h1><p>%s</p></body></html>""".formatted(prose())));
        // Flex and a background: the two things the previous renderer could not do, so
        // they are what distinguishes a browser-rendered archive from the old one.
        server.createContext("/modern", exchange -> respond(exchange, """
                <html><head><title>Modern</title><style>
                  body { margin: 0; }
                  .row { display: flex; gap: 20px; padding: 24px;
                         background: linear-gradient(90deg, #123456, #abcdef); }
                  .cell { flex: 1; color: white; font-weight: 700; }
                </style></head>
                <body><div class="row">
                  <div class="cell">Left column</div>
                  <div class="cell">Right column</div>
                </div><p>%s</p></body></html>""".formatted(prose())));
        // The case docs/issues/002 was about: the real image only appears once script
        // runs. A browser runs it; the old renderer had to chase the attribute by hand.
        server.createContext("/deferred", exchange -> respond(exchange, """
                <html><head><title>Deferred</title></head>
                <body><h1>Deferred</h1>
                <div id="holder"></div>
                <script>
                  const box = document.createElement('div');
                  box.style.cssText = 'width:400px;height:300px;background:#c0ffee';
                  box.textContent = 'drawn by script';
                  document.getElementById('holder').appendChild(box);
                </script>
                <p>%s</p></body></html>""".formatted(prose())));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("prints a real PDF carrying the page's own words")
    void printsAPdf() {
        byte[] pdf = archive(url("/plain")).orElseThrow(() -> new AssertionError("nothing archived"));

        assertArrayEquals(PDF_MAGIC, Arrays.copyOf(pdf, PDF_MAGIC.length));
        assertTrue(new String(pdf, StandardCharsets.ISO_8859_1).contains("%%EOF"),
                "a PDF ends with its trailer");
        // The assertion that matters: a browser that reached nothing prints a blank
        // page, and a blank page has the magic bytes and the trailer too.
        assertTrue(textOf(pdf).contains("A plain page"),
                "the archive should carry the heading the page served");
    }

    /**
     * The whole reason for using a browser. A gradient background and a flex row are
     * both beyond CSS 2.1, so the rendered page is substantially larger than the plain
     * one — evidence the stylesheet was actually applied rather than ignored.
     */
    @Test
    @DisplayName("applies the CSS the old renderer could not")
    void appliesModernCss() {
        byte[] plain = archive(url("/plain")).orElseThrow();
        byte[] modern = archive(url("/modern")).orElseThrow();

        assertTrue(textOf(modern).contains("Left column"), "the flex row should have been laid out");
        assertTrue(modern.length > plain.length,
                "a gradient and a flex row should cost more bytes than a bare page, but was "
                        + modern.length + " against " + plain.length);
    }

    /**
     * The case docs/issues/002 was about, and the reason a browser does the rendering:
     * this text exists only after the page's script has run.
     */
    @Test
    @DisplayName("captures what the page's own script drew")
    void capturesScriptedContent() {
        assertTrue(textOf(archive(url("/deferred")).orElseThrow()).contains("drawn by script"),
                "content the script added should be in the archive");
    }

    /**
     * A browser that is not there must cost the archive, not the process.
     */
    @Test
    @DisplayName("comes back empty when the browser cannot be reached")
    void survivesAnAbsentBrowser() {
        ArchiveProperties nowhere = new ArchiveProperties(
                "http://127.0.0.1:1", Duration.ofSeconds(2), 1280);

        assertTrue(new BrowserPageArchiver(nowhere)
                .archive(null, "Title", url("/plain"), 0L).isEmpty());
    }

    /**
     * Every archive opens a tab of its own, and a leaked one holds its share of the
     * browser's memory until the sidecar restarts. Counted rather than assumed.
     */
    @Test
    @DisplayName("leaves no tab behind")
    void closesItsTab() throws Exception {
        int before = openPages();

        archive(url("/plain")).orElseThrow();

        assertEquals(before, openPages(), "the archive's tab should have been closed");
    }

    private static Optional<byte[]> archive(String pageUrl) {
        return new BrowserPageArchiver(properties()).archive(null, "Title", pageUrl, 1_755_000_000_000L);
    }

    private static ArchiveProperties properties() {
        return new ArchiveProperties(browserUrl(), Duration.ofSeconds(30), 1280);
    }

    private static String browserUrl() {
        return ArchivingBrowser.url();
    }

    /**
     * How many pages the browser currently has open, read from its own listing.
     */
    private static int openPages() throws Exception {
        java.net.http.HttpResponse<String> list = java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(browserUrl() + "/json/list")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return Jsoup.parse(list.body()).text().split("\"type\": \"page\"", -1).length - 1;
    }

    private static String prose() {
        return "Words enough to make a page worth rendering. ".repeat(10);
    }

    private static void respond(HttpExchange exchange, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String url(String path) {
        return "http://" + ArchivingBrowser.hostAddress(server.getAddress().getPort()) + path;
    }

    /**
     * The text the browser actually laid out. A blank page is still a valid PDF with
     * the right magic bytes, so this is the only assertion that can tell "archived the
     * page" from "archived nothing".
     */
    private static String textOf(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ").trim();
        } catch (IOException unreadable) {
            throw new AssertionError("the archive was not a readable PDF", unreadable);
        }
    }
}
