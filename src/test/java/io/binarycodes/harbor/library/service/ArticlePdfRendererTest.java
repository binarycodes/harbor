package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Rendering the archive. The images come from a real server on loopback, permitted
 * by range exactly as {@code HttpDocumentLoaderTest} does it, so the guarded client
 * is the one doing the fetching.
 *
 * <p>What is asserted is mostly size: a PDF that renders is easy, and a PDF that
 * quietly left every picture out renders just as happily. Only the byte count tells
 * those two apart.
 */
@DisplayName("Archiving an article as a PDF")
class ArticlePdfRendererTest {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.UTF_8);
    private static final long ARCHIVED_AT = 1_755_000_000_000L;

    private HttpServer server;
    private AtomicInteger imageRequests;

    @BeforeEach
    void startServer() throws IOException {
        imageRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/photo.png", exchange -> {
            imageRequests.incrementAndGet();
            respond(exchange, "image/png", picture(240));
        });
        server.createContext("/huge.png", exchange -> {
            imageRequests.incrementAndGet();
            respond(exchange, "image/png", picture(1400));
        });
        server.createContext("/not-an-image", exchange -> {
            imageRequests.incrementAndGet();
            respond(exchange, "text/html", "<p>nope</p>".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/missing", exchange -> {
            imageRequests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("produces a real PDF from an article")
    void producesAPdf() {
        byte[] pdf = archive("<article><h1>Title</h1><p>%s</p></article>".formatted(prose()))
                .orElseThrow(() -> new AssertionError("nothing was archived"));

        assertArrayEquals(PDF_MAGIC, Arrays.copyOf(pdf, PDF_MAGIC.length));
        assertTrue(new String(pdf, StandardCharsets.ISO_8859_1).contains("%%EOF"),
                "a PDF ends with its trailer");
    }

    /**
     * The whole point of the archive. A PDF renders happily with every picture
     * missing, so the assertion is that embedding one actually costs bytes.
     */
    @Test
    @DisplayName("embeds the article's images, and grows by roughly their size")
    void embedsImages() {
        int withoutImage = archive(
                "<article><p>%s</p></article>".formatted(prose())).orElseThrow().length;

        int withImage = archive("<article><p>%s</p><img src='%s'/></article>"
                .formatted(prose(), url("/photo.png"))).orElseThrow().length;

        assertEquals(1, imageRequests.get());
        assertTrue(withImage > withoutImage + 5_000,
                "embedding a 240px picture should cost real bytes, but grew only "
                        + (withImage - withoutImage));
    }

    /**
     * The case from docs/issues/002: the real URL is in data-src and src holds a
     * transparent pixel. Reading src alone archives a blank.
     */
    @Test
    @DisplayName("finds a deferred image rather than archiving its placeholder")
    void embedsADeferredImage() {
        int withoutImage = archive(
                "<article><p>%s</p></article>".formatted(prose())).orElseThrow().length;

        int withImage = archive("""
                <article><p>%s</p>
                <img src="data:image/gif;base64,R0lGODlhAQABAAAAACw=" data-src="%s"/>
                </article>""".formatted(prose(), url("/photo.png"))).orElseThrow().length;

        assertEquals(1, imageRequests.get());
        assertTrue(withImage > withoutImage + 5_000, "the deferred image should have been embedded");
    }

    @Test
    @DisplayName("archives without an image whose host the deployment refuses")
    void skipsABlockedImage() {
        // No allowed ranges, so loopback is refused exactly as any private address is.
        byte[] pdf = renderer(List.of()).archive(
                document("<article><p>%s</p><img src='%s'/></article>"
                        .formatted(prose(), url("/photo.png"))),
                "Title", "https://example.com/one", ARCHIVED_AT)
                .orElseThrow(() -> new AssertionError("a refused image must not lose the archive"));

        assertArrayEquals(PDF_MAGIC, Arrays.copyOf(pdf, PDF_MAGIC.length));
    }

    @Test
    @DisplayName("archives without an image served larger than the budget allows")
    void skipsAnOversizeImage() {
        int withoutImage = archive(
                "<article><p>%s</p></article>".formatted(prose())).orElseThrow().length;

        int withHuge = archive("<article><p>%s</p><img src='%s'/></article>"
                .formatted(prose(), url("/huge.png"))).orElseThrow().length;

        assertTrue(withHuge < withoutImage + 2_000,
                "an oversize image should have been left out, but the PDF grew by "
                        + (withHuge - withoutImage));
    }

    @Test
    @DisplayName("archives without something that is not an image at all")
    void skipsANonImage() {
        byte[] pdf = archive("<article><p>%s</p><img src='%s'/></article>"
                .formatted(prose(), url("/not-an-image"))).orElseThrow();

        assertArrayEquals(PDF_MAGIC, Arrays.copyOf(pdf, PDF_MAGIC.length));
    }

    @Test
    @DisplayName("archives without an image the host has lost")
    void skipsAMissingImage() {
        byte[] pdf = archive("<article><p>%s</p><img src='%s'/></article>"
                .formatted(prose(), url("/missing"))).orElseThrow();

        assertArrayEquals(PDF_MAGIC, Arrays.copyOf(pdf, PDF_MAGIC.length));
    }

    /**
     * Keeping a spacer would stretch a single transparent pixel across the page, so
     * the element goes. Asserted on the document rather than the PDF: a spacer that
     * survives costs about eighty bytes, which no size assertion can see.
     */
    @Test
    @DisplayName("drops an inline spacer from the document")
    void dropsAnInlineSpacer() {
        Element root = articleWith("<img src='data:image/gif;base64,R0lGODlhAQABAAAAACw='/>");

        renderer(List.of("127.0.0.0/8")).inlineImages(root);

        assertEquals(0, root.select("img").size(), "a transparent pixel is not a picture");
    }

    @Test
    @DisplayName("keeps inline bytes that are a picture, without fetching anything")
    void keepsInlineBytes() {
        String inlined = "data:image/png;base64," + Base64.getEncoder().encodeToString(picture(240));
        Element root = articleWith("<img src='%s'/>".formatted(inlined));

        renderer(List.of("127.0.0.0/8")).inlineImages(root);

        assertEquals(1, root.select("img").size());
        assertTrue(root.selectFirst("img").attr("src").startsWith("data:image/png"));
        assertEquals(0, imageRequests.get(), "inline bytes need no fetching");
    }

    @Test
    @DisplayName("strips the attributes that would send the renderer looking itself")
    void stripsResolvingAttributes() {
        Element root = articleWith("<img src='data:image/gif;base64,R0lGODlh' data-src='%s' "
                .formatted(url("/photo.png")) + "srcset='%s 800w' loading='lazy'/>"
                .formatted(url("/photo.png")));

        renderer(List.of("127.0.0.0/8")).inlineImages(root);

        Element image = root.selectFirst("img");
        assertTrue(image.attr("src").startsWith("data:image/png;base64,"), "the bytes are embedded");
        assertEquals("", image.attr("srcset"));
        assertEquals("", image.attr("data-src"));
        assertEquals("", image.attr("loading"));
    }

    @Test
    @DisplayName("archives nothing when the page has no article worth keeping")
    void archivesNothingWithoutAnArticle() {
        assertTrue(archive("<p>too short to be an article</p>").isPresent(),
                "a thin page still renders; it is the reader's text that has a floor");
        assertTrue(renderer(List.of("127.0.0.0/8")).archive(null, "Title", "https://example.com", 0L)
                .isEmpty(), "no document means no archive");
    }

    private Optional<byte[]> archive(String bodyHtml) {
        return renderer(List.of("127.0.0.0/8"))
                .archive(document(bodyHtml), "Title", "https://example.com/one", ARCHIVED_AT);
    }

    private Element articleWith(String imageHtml) {
        return ArticleContent.cleaned(
                document("<article><p>%s</p>%s</article>".formatted(prose(), imageHtml)));
    }

    private Document document(String bodyHtml) {
        return Jsoup.parse("<html><body>%s</body></html>".formatted(bodyHtml), url("/"));
    }

    private static ArticlePdfRenderer renderer(List<String> allowedRanges) {
        OutboundFetchProperties fetch = new OutboundFetchProperties(
                ReservedAddressRanges.notations(), allowedRanges, Duration.ofSeconds(5), 5, 512 * 1024);
        ArchiveProperties archive = new ArchiveProperties(25, 256 * 1024, 4 * 1024 * 1024);
        GuardedHttpClient client = new GuardedHttpClient(
                new GuardedDnsResolver(new OutboundAddressPolicy(fetch)), fetch);
        return new ArticlePdfRenderer(new ArticleImageLoader(client, archive), archive);
    }

    /**
     * A PNG of noise, because a flat colour compresses to almost nothing and would
     * make the size assertions meaningless.
     */
    private static byte[] picture(int side) {
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                image.setRGB(x, y, new Color((x * 7 + y * 13) % 256, (x * 3) % 256, (y * 5) % 256).getRGB());
            }
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException cannotEncode) {
            throw new IllegalStateException(cannotEncode);
        }
    }

    private static String prose() {
        return "Words enough to make a page that is worth rendering. ".repeat(12);
    }

    private static void respond(HttpExchange exchange, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
