package io.binarycodes.harbor.library.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * What makes two links the same link. Saving a page twice is almost always a
 * mistake, but the same page is reached by URLs that differ in ways that do not
 * matter: the host's capitalisation, an explicit default port, a trailing slash, the
 * fragment that only scrolls the browser somewhere.
 *
 * <p>What it deliberately does not fold together: {@code http} and {@code https},
 * {@code www} and the bare host, and anything differing in the query string. Those
 * can each serve genuinely different pages, and wrongly refusing to save a link is
 * worse than keeping two copies of one.
 */
final class UrlKey {

    private UrlKey() {
    }

    static String of(String url) {
        String trimmed = url == null ? "" : url.strip();
        try {
            URI parsed = new URI(trimmed);
            if (parsed.getHost() == null) {
                return trimmed.toLowerCase(Locale.ROOT);
            }
            return new URI(
                    lower(parsed.getScheme()),
                    null,
                    lower(parsed.getHost()),
                    meaningfulPort(parsed),
                    withoutTrailingSlash(parsed.getPath()),
                    parsed.getQuery(),
                    null).toString();
        } catch (URISyntaxException notAUrl) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A port only distinguishes a URL when it is not the one the scheme implies.
     */
    private static int meaningfulPort(URI parsed) {
        int port = parsed.getPort();
        String scheme = lower(parsed.getScheme());
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            return -1;
        }
        return port;
    }

    private static String withoutTrailingSlash(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "";
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
