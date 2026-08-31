package io.binarycodes.harbor.library.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * What a reader types, turned into something that can actually be fetched. Nobody
 * types a scheme, so {@code vaadin.com} means {@code https://vaadin.com} and is
 * completed to it here.
 *
 * <p>That completion has to happen once, early, and be carried everywhere after:
 * jsoup is forgiving about a bare host and a headless browser is not — Chromium
 * leaves the tab on {@code about:blank} rather than reporting anything — so a URL
 * normalised for only one of them produces a page that reads fine and archives
 * blank. It is also what the bookmark stores, since a stored {@code vaadin.com} is
 * a relative link everywhere it is later rendered.
 *
 * <p>Only http and https are completed or accepted. A link a reader pasted is
 * untrusted input, and every other scheme a URI can name would have the server read
 * something it has no business reading.
 */
public final class AbsoluteUrl {

    private static final String HAS_SCHEME = "(?i)^[a-z][a-z0-9+.-]*://.*";

    private AbsoluteUrl() {
    }

    /**
     * @throws IllegalArgumentException if what is left after completing the scheme is
     *                                  not an http or https URL with a host
     */
    public static String of(String url) {
        String candidate = url == null ? "" : url.strip();
        if (!candidate.matches(HAS_SCHEME)) {
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
     * The same, for a caller with nothing useful to do about a URL it cannot parse —
     * a field the reader is still halfway through typing, most of it.
     *
     * @return the completed URL, or what was passed in when it is not one
     */
    public static String ofOrSame(String url) {
        try {
            return of(url);
        } catch (IllegalArgumentException notAUrl) {
            return url;
        }
    }
}
