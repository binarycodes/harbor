package io.binarycodes.harbor.library.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the browser that renders the archive lives, and how long it is given.
 *
 * <p>Harbor cannot archive without it, and it does not save what it cannot archive,
 * so a deployment with no browser configured is not a degraded Harbor — it is a
 * Harbor that cannot file a bookmark. That is worth failing at startup for rather
 * than discovering at the reader's first save.
 *
 * @param browserUrl     the sidecar's DevTools endpoint, e.g.
 *                       {@code http://chromium:9222}
 * @param browserTimeout how long any one step of a render may take. The reader is
 *                       waiting on this before they can save
 * @param viewportWidth  the width the page is laid out at. Layout depends on it, so
 *                       it is fixed here rather than left to whatever the browser
 *                       defaults to
 */
@ConfigurationProperties("harbor.archive")
public record ArchiveProperties(String browserUrl, Duration browserTimeout, int viewportWidth) {

    public ArchiveProperties {
        if (browserUrl == null || browserUrl.isBlank()) {
            throw new IllegalStateException(
                    "harbor.archive.browser-url must name a Chromium DevTools endpoint"
                            + " (e.g. http://chromium:9222). Harbor archives every page it saves and"
                            + " will not save a page it cannot archive, so it cannot start without"
                            + " one. See environment/dev.");
        }
        browserTimeout = browserTimeout == null ? Duration.ofSeconds(30) : browserTimeout;
        viewportWidth = viewportWidth <= 0 ? 1280 : viewportWidth;
    }
}
