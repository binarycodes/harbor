package io.binarycodes.harbor.library.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the browser that renders the archive lives, how long it is given, and
 * whether the reader waits for it.
 *
 * <p>Harbor cannot archive without a browser, so a deployment with none configured is
 * not a degraded Harbor — it is a Harbor that cannot keep a copy of anything. That is
 * worth failing at startup for rather than discovering at the reader's first save.
 *
 * @param browserUrl      the sidecar's DevTools endpoint, e.g.
 *                        {@code http://chromium:9222}
 * @param browserTimeout  how long any one step of a render may take
 * @param viewportWidth   the width the page is laid out at. Layout depends on it, so
 *                        it is fixed here rather than left to whatever the browser
 *                        defaults to
 * @param forceBeforeSave whether a bookmark may only exist once its archive does. True
 *                        holds the save open until the render finishes and refuses a
 *                        page that will not render; false files the bookmark at once
 *                        and renders afterwards. The first costs the reader seconds on
 *                        every save, the second admits a bookmark whose archive has
 *                        not arrived yet — and might never
 */
@ConfigurationProperties("harbor.archive")
public record ArchiveProperties(String browserUrl, Duration browserTimeout, int viewportWidth,
        Boolean forceBeforeSave) {

    public ArchiveProperties {
        if (browserUrl == null || browserUrl.isBlank()) {
            throw new IllegalStateException(
                    "harbor.archive.browser-url must name a Chromium DevTools endpoint"
                            + " (e.g. http://chromium:9222). Harbor archives every page it saves and"
                            + " cannot start without one. See environment/dev.");
        }
        browserTimeout = browserTimeout == null ? Duration.ofSeconds(30) : browserTimeout;
        viewportWidth = viewportWidth <= 0 ? 1280 : viewportWidth;
        // Boxed only so that an absent value is distinguishable from a configured
        // false. Leaving it out has to mean the stricter of the two: the alternative
        // is a deployment that quietly stops guaranteeing archives because a property
        // was forgotten.
        forceBeforeSave = forceBeforeSave == null || forceBeforeSave;
    }
}
