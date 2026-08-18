package io.binarycodes.harbor.library.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fetches one image for the archive, through the same guarded client the page
 * itself came through — so an image host is vetted exactly as the page host was.
 *
 * <p>Nothing here throws. An image that cannot be had is an image the archive does
 * without: a figure hosted somewhere the deployment refuses, or served too large,
 * or in a format that cannot be embedded, is not a reason to fail a save.
 */
@Component
class ArticleImageLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleImageLoader.class);
    private static final Set<String> EMBEDDABLE_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif");
    private static final int ERROR_STATUS_FLOOR = 400;

    private final GuardedHttpClient httpClient;
    private final ArchiveProperties properties;

    ArticleImageLoader(GuardedHttpClient httpClient, ArchiveProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    /**
     * @return the image, or empty when it could not be had for any reason
     */
    Optional<InlineImage> load(String url) {
        HttpGet request = new HttpGet(url);
        request.setHeader("User-Agent", GuardedHttpClient.USER_AGENT);
        request.setHeader("Accept", "image/jpeg,image/png,image/gif");
        try {
            return httpClient.client().execute(request, response -> {
                if (response.getCode() >= ERROR_STATUS_FLOOR) {
                    LOGGER.debug("Leaving out {}: answered {}", url, response.getCode());
                    return Optional.empty();
                }
                return read(response.getEntity(), url);
            });
        } catch (IOException | RuntimeException unreachable) {
            // BlockedAddressException arrives here too: a refused address is a figure
            // the archive goes without, not a save that fails.
            LOGGER.debug("Leaving out {}: {}", url, unreachable.getMessage());
            return Optional.empty();
        }
    }

    private Optional<InlineImage> read(HttpEntity entity, String url) throws IOException {
        if (entity == null) {
            return Optional.empty();
        }
        ContentType contentType = ContentType.parseLenient(entity.getContentType());
        String mimeType = contentType == null
                ? ""
                : contentType.getMimeType().toLowerCase(Locale.ROOT);
        if (!EMBEDDABLE_TYPES.contains(mimeType)) {
            LOGGER.debug("Leaving out {}: served {}, which cannot be embedded", url, mimeType);
            return Optional.empty();
        }
        try (InputStream body = entity.getContent()) {
            // One byte past the limit, so that too-large is told apart from
            // exactly-at-the-limit. A truncated image is corrupt, not smaller.
            byte[] bytes = body.readNBytes(properties.maxImageBytes() + 1);
            if (bytes.length > properties.maxImageBytes()) {
                LOGGER.debug("Leaving out {}: larger than {} bytes", url, properties.maxImageBytes());
                return Optional.empty();
            }
            return bytes.length == 0 ? Optional.empty() : Optional.of(new InlineImage(bytes, mimeType));
        }
    }

    /**
     * An image's bytes and what they are, ready to be written into the document as a
     * {@code data:} URI.
     */
    record InlineImage(byte[] bytes, String contentType) {
    }
}
