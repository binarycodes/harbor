package io.binarycodes.harbor.library.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities.EscapeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Renders the article to a PDF that keeps its pictures, which is the one thing the
 * Markdown in the reader cannot do.
 *
 * <p>Every image is fetched here and written into the document as a {@code data:}
 * URI before the renderer sees it, and the renderer is then given a stream factory
 * that refuses http and https outright. That ordering is the point: left to itself
 * the renderer would resolve {@code <img src>} through its own connections, with
 * none of {@link GuardedDnsResolver}'s vetting on them — a second way out, and so
 * no guard at all.
 *
 * <p>Nothing here throws. A page that will not render is a bookmark without an
 * archive, never a save that fails.
 */
@Component
class ArticlePdfRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArticlePdfRenderer.class);
    private static final DateTimeFormatter ARCHIVED_ON =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    /**
     * Below this, a {@code data:} image is a spacer holding the layout open rather
     * than a picture — publishers use a transparent pixel, which base64s to well
     * under a hundred bytes. Keeping one would stretch it across the page, so the
     * element goes instead.
     */
    private static final int SMALLEST_INLINE_PICTURE = 512;

    private static final String DOCUMENT = """
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
            <title>%s</title>
            <style>%s</style>
            </head>
            <body>
            <div class="provenance">%s<span class="source">%s</span></div>
            %s
            </body>
            </html>""";

    private final ArticleImageLoader imageLoader;
    private final ArchiveProperties properties;

    ArticlePdfRenderer(ArticleImageLoader imageLoader, ArchiveProperties properties) {
        this.imageLoader = imageLoader;
        this.properties = properties;
    }

    /**
     * @return the archived article, or empty when the page has no article worth
     *         keeping or would not render
     */
    Optional<byte[]> render(Document document, String title, String url, long archivedAt) {
        Element root = ArticleContent.cleaned(document);
        if (root == null) {
            return Optional.empty();
        }
        try {
            inlineImages(root);
            return Optional.of(toPdf(xhtml(root, title, url, archivedAt), url));
        } catch (IOException | RuntimeException wontRender) {
            LOGGER.warn("Could not archive {}: {}", url, wontRender.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Replaces every image with its own bytes, and drops the ones that cannot be
     * had — an {@code <img>} left pointing at a URL would either render as nothing
     * or send the renderer looking for it.
     *
     * <p>Package-private so a test can count what survived. The PDF's size cannot
     * answer that: a dropped spacer and a kept one differ by a handful of bytes.
     */
    void inlineImages(Element root) {
        int embedded = 0;
        int spent = 0;
        for (Element image : root.select("img")) {
            if (keepsItsOwnBytes(image)) {
                strip(image);
                continue;
            }
            if (embedded >= properties.maxImages()) {
                image.remove();
                continue;
            }
            Optional<ArticleImageLoader.InlineImage> loaded =
                    ArticleImageSource.bestFor(image).flatMap(imageLoader::load);
            if (loaded.isEmpty() || spent + loaded.get().bytes().length > properties.maxTotalBytes()) {
                image.remove();
                continue;
            }
            strip(image);
            image.attr("src", dataUri(loaded.get()));
            embedded++;
            spent += loaded.get().bytes().length;
        }
    }

    private static boolean keepsItsOwnBytes(Element image) {
        String src = image.attr("src");
        return src.startsWith("data:") && src.length() > SMALLEST_INLINE_PICTURE;
    }

    /**
     * The attributes that would have the renderer resolve something for itself, once
     * the bytes are already in hand.
     */
    private static void strip(Element image) {
        List.of("srcset", "data-src", "data-srcset", "data-original", "data-lazy-src",
                "data-actualsrc", "loading", "sizes").forEach(image::removeAttr);
    }

    private static String dataUri(ArticleImageLoader.InlineImage image) {
        return "data:" + image.contentType() + ";base64,"
                + Base64.getEncoder().encodeToString(image.bytes());
    }

    /**
     * jsoup's XML syntax, because the renderer wants well-formed XHTML: unclosed
     * void elements and bare ampersands are both ordinary in HTML and fatal here.
     */
    private String xhtml(Element root, String title, String url, long archivedAt) {
        Document.OutputSettings settings = new Document.OutputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(EscapeMode.xhtml)
                .prettyPrint(false)
                .charset(StandardCharsets.UTF_8);
        root.ownerDocument().outputSettings(settings);
        return DOCUMENT.formatted(
                escaped(title),
                stylesheet(),
                "Archived " + ARCHIVED_ON.format(Instant.ofEpochMilli(archivedAt)) + " from ",
                escaped(url),
                root.outerHtml());
    }

    private static String escaped(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String stylesheet() {
        try (InputStream css = new ClassPathResource("pdf/article.css").getInputStream()) {
            return new String(css.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException missing) {
            // Unstyled is still readable; failing to archive over a stylesheet is not.
            LOGGER.warn("The archive stylesheet could not be read; rendering unstyled", missing);
            return "";
        }
    }

    private byte[] toPdf(String xhtml, String baseUri) throws IOException {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(xhtml, baseUri);
        builder.useProtocolsStreamImplementation(new RefusesToFetch(), "http", "https");
        builder.toStream(pdf);
        builder.run();
        return pdf.toByteArray();
    }

    /**
     * Closes the renderer's own way onto the network. Everything it needs is already
     * embedded, so a request reaching here is a bug — but one that should cost a
     * missing picture and a log line, not the whole archive.
     */
    private static final class RefusesToFetch implements FSStreamFactory {

        @Override
        public FSStream getUrl(String url) {
            LOGGER.warn("The renderer asked for {} itself; refusing, since it was not embedded", url);
            return new FSStream() {

                @Override
                public InputStream getStream() {
                    return InputStream.nullInputStream();
                }

                @Override
                public Reader getReader() {
                    return Reader.nullReader();
                }
            };
        }
    }
}
