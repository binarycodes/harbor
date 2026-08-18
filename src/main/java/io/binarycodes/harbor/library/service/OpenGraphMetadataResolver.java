package io.binarycodes.harbor.library.service;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

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
 * {@link UrlHeuristicMetadata}, and so does any failure to reach it at all: a link
 * can still be described when the host is down, behind a paywall, or refusing
 * robots. The result reports which of the two happened through
 * {@link LinkMetadata#pageRead()}, leaving the caller to decide whether a
 * description is enough to act on — the save dialog holds out for a real read.
 *
 * <p>An address the deployment refuses to fetch is the exception: that is a decision
 * worth reporting rather than a page that happened to be unreachable.
 *
 * <p>Reading the page is also the moment it is archived, since it is the one moment
 * the page is known to be reachable. A page that will not render is saved without an
 * archive rather than not saved.
 */
@Component
public class OpenGraphMetadataResolver implements MetadataResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenGraphMetadataResolver.class);

    private final DocumentLoader documentLoader;
    private final ArticleArchiver archiver;
    private final Clock clock;
    private final UrlHeuristicMetadata fallback = new UrlHeuristicMetadata();

    OpenGraphMetadataResolver(DocumentLoader documentLoader, ArticleArchiver archiver, Clock clock) {
        this.documentLoader = documentLoader;
        this.archiver = archiver;
        this.clock = clock;
    }

    @Override
    public LinkMetadata resolve(String url) {
        LinkMetadata fromUrl = fallback.resolve(url);
        Document document;
        try {
            document = documentLoader.load(absolute(url));
        } catch (BlockedAddressException blocked) {
            LOGGER.warn("Refused to fetch {}: {} is outside the ranges this deployment allows", url,
                    blocked.getAddress());
            throw new AddressNotAllowedException(blocked);
        } catch (IOException | IllegalArgumentException unreachable) {
            LOGGER.info("Could not read {}, describing it from the URL instead: {}", url,
                    unreachable.getMessage());
            return fromUrl;
        }

        String content = ArticleExtractor.toMarkdown(document);
        String title = firstNonBlank(meta(document, "og:title"), document.title(), fromUrl.title());
        // The page is already in hand, so the archive costs its images and nothing
        // else. It is rendered here rather than later because this is the one moment
        // the page is known to be reachable.
        byte[] archive = archiver.archive(document, title, url, clock.millis()).orElse(null);
        return new LinkMetadata(
                firstNonBlank(meta(document, "og:site_name"), fromUrl.site()),
                title,
                firstNonBlank(meta(document, "og:description"), meta(document, "description")),
                fromUrl.tags(),
                typeOf(document, fromUrl.type()),
                ReadingTime.minutes(content),
                content,
                true,
                archive);
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

}
