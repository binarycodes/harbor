package io.binarycodes.harbor.library.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.binarycodes.harbor.library.domain.BookmarkType;

/**
 * Reads a page to find out what it is: the Open Graph tags first, since that is
 * what publishers maintain for exactly this purpose, then the document's own title
 * and description, and finally the article text for the reader.
 *
 * <p>Anything the page does not tell us falls through to
 * {@link UrlHeuristicMetadata}, and so does any failure to reach it at all —
 * saving a link must work offline, behind a paywall, and against a host that
 * refuses robots.
 */
@Component
public class OpenGraphMetadataResolver implements MetadataResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenGraphMetadataResolver.class);

    private final DocumentLoader documentLoader;
    private final UrlHeuristicMetadata fallback = new UrlHeuristicMetadata();

    public OpenGraphMetadataResolver() {
        this(new JsoupDocumentLoader());
    }

    OpenGraphMetadataResolver(DocumentLoader documentLoader) {
        this.documentLoader = documentLoader;
    }

    @Override
    public LinkMetadata resolve(String url) {
        LinkMetadata fromUrl = fallback.resolve(url);
        Document document;
        try {
            document = documentLoader.load(absolute(url));
        } catch (IOException | IllegalArgumentException unreachable) {
            LOGGER.info("Could not read {}, describing it from the URL instead: {}", url,
                    unreachable.getMessage());
            return fromUrl;
        }

        String content = ArticleExtractor.toMarkdown(document);
        return new LinkMetadata(
                firstNonBlank(meta(document, "og:site_name"), fromUrl.site()),
                firstNonBlank(meta(document, "og:title"), document.title(), fromUrl.title()),
                firstNonBlank(meta(document, "og:description"), meta(document, "description")),
                fromUrl.tags(),
                typeOf(document, fromUrl.type()),
                ReadingTime.minutes(content),
                content);
    }

    /**
     * Open Graph declares a handful of page kinds. Only the ones Harbor
     * distinguishes are honoured; anything else keeps what the host suggested.
     */
    private static BookmarkType typeOf(Document document, BookmarkType fromUrl) {
        String openGraphType = meta(document, "og:type").toLowerCase(Locale.ROOT);
        if (openGraphType.startsWith("video")) {
            return BookmarkType.VIDEO;
        }
        if (openGraphType.equals("book")) {
            return BookmarkType.BOOK;
        }
        return fromUrl;
    }

    private static String meta(Document document, String name) {
        String byProperty = document.select("meta[property=" + name + "]").attr("content");
        return byProperty.isBlank() ? document.select("meta[name=" + name + "]").attr("content") : byProperty;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.strip();
            }
        }
        return "";
    }

    /**
     * Only http and https are followed. A link the reader pasted is untrusted input,
     * and every other scheme a URI can name would have the server read something it
     * has no business reading.
     */
    static String absolute(String url) {
        String candidate = url == null ? "" : url.strip();
        if (!candidate.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            candidate = "https://" + candidate;
        }
        try {
            URI parsed = new URI(candidate);
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            if (!List.of("http", "https").contains(scheme)) {
                throw new IllegalArgumentException("Unsupported URL scheme: " + scheme);
            }
            if (parsed.getHost() == null || parsed.getHost().isBlank()) {
                throw new IllegalArgumentException("URL has no host: " + url);
            }
            return parsed.toString();
        } catch (URISyntaxException malformed) {
            throw new IllegalArgumentException("Not a URL: " + url, malformed);
        }
    }

    /**
     * The seam that keeps the network out of the tests.
     */
    interface DocumentLoader {

        Document load(String url) throws IOException;
    }

    private static class JsoupDocumentLoader implements DocumentLoader {

        private static final Duration TIMEOUT = Duration.ofSeconds(8);
        private static final int MAX_BODY_BYTES = 512 * 1024;
        private static final String USER_AGENT = "Mozilla/5.0 (compatible; Harbor/1.0; +local-first bookmark manager)";

        @Override
        public Document load(String url) throws IOException {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout((int) TIMEOUT.toMillis())
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(true)
                    .ignoreHttpErrors(false)
                    .ignoreContentType(false)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .get();
        }
    }
}
