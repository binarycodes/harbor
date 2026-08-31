package io.binarycodes.harbor.library.service;

import java.io.IOException;
import java.time.Clock;
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
 * <p>Reading the page is also the moment it is archived, when the deployment holds
 * the save open for it — that is the one moment the page is known to be reachable, and
 * paying for the render here is what lets a saved bookmark always have its copy.
 * Where {@code harbor.archive.force-before-save} is off the render is somebody else's
 * job: the metadata comes back with no archive, and
 * {@link BackgroundArchiver} produces one once the bookmark exists to hang it on.
 */
@Component
public class OpenGraphMetadataResolver implements MetadataResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenGraphMetadataResolver.class);

    private final DocumentLoader documentLoader;
    private final ArticleArchiver archiver;
    private final ArchiveProperties archiveProperties;
    private final Clock clock;
    private final UrlHeuristicMetadata fallback = new UrlHeuristicMetadata();

    OpenGraphMetadataResolver(DocumentLoader documentLoader, ArticleArchiver archiver,
            ArchiveProperties archiveProperties, Clock clock) {
        this.documentLoader = documentLoader;
        this.archiver = archiver;
        this.archiveProperties = archiveProperties;
        this.clock = clock;
    }

    @Override
    public LinkMetadata resolve(String url) {
        // Completed once, here, and used for everything downstream. The archiver gets
        // this rather than what was typed: jsoup tolerates a bare host and Chromium
        // does not, so passing the raw URL on produced a page that read fine and
        // archived blank.
        String address = AbsoluteUrl.ofOrSame(url);
        LinkMetadata fromUrl = fallback.resolve(address);
        Document document;
        try {
            document = documentLoader.load(AbsoluteUrl.of(address));
        } catch (BlockedAddressException blocked) {
            LOGGER.warn("Refused to fetch {}: {} is outside the ranges this deployment allows", address,
                    blocked.getAddress());
            throw new AddressNotAllowedException(blocked);
        } catch (IOException | IllegalArgumentException unreachable) {
            LOGGER.info("Could not read {}, describing it from the URL instead: {}", address,
                    unreachable.getMessage());
            return fromUrl;
        }

        String content = ArticleExtractor.toMarkdown(document);
        String title = firstNonBlank(meta(document, "og:title"), document.title(), fromUrl.title());
        byte[] archive = archiveProperties.forceBeforeSave()
                ? archiver.archive(title, address, clock.millis()).orElse(null)
                : null;
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

}
