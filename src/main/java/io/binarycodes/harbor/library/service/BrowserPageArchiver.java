package io.binarycodes.harbor.library.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Archives a page by having a real browser print it. Chromium runs the scripts,
 * applies the real stylesheets and loads the web fonts, so what comes back is the
 * page as a reader would have seen it rather than a reconstruction of its article.
 *
 * <p>It takes no parsed document. Harbor has usually already fetched the page to
 * describe it, but the browser has to fetch it again for itself, and re-serialising
 * what jsoup parsed would hand the browser something subtly different from what the
 * site serves.
 *
 * <p>Nothing here throws. A page that will not render comes back empty, and it is
 * the save gate that decides what to do about it.
 */
@Component
class BrowserPageArchiver implements ArticleArchiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserPageArchiver.class);

    /**
     * Long enough for a lazy-loading page to settle after being scrolled, short
     * enough not to be felt on top of everything else the reader is waiting for.
     */
    private static final Duration SETTLE = Duration.ofMillis(750);

    private static final String SCROLL_THROUGH = """
            (async () => {
              const step = () => new Promise(r => requestAnimationFrame(() => r()));
              for (let y = 0; y < document.body.scrollHeight; y += window.innerHeight) {
                window.scrollTo(0, y);
                await step();
              }
              window.scrollTo(0, 0);
              await step();
            })()""";

    private final ArchiveProperties properties;

    /**
     * Its own client, not {@link GuardedHttpClient}: the sidecar is a configured
     * collaborator, and its address is exactly the kind of private one the guard
     * exists to refuse. The guard is for URLs a visitor supplies, never for Harbor's
     * own infrastructure.
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    BrowserPageArchiver(ArchiveProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<byte[]> archive(String title, String url, long archivedAt) {
        Duration timeout = properties.browserTimeout();
        try (DevToolsSession session = DevToolsSession.open(httpClient, socketUrl(timeout), timeout)) {
            return Optional.of(print(session, url, timeout));
        } catch (DevToolsSession.DevToolsException wontRender) {
            LOGGER.warn("Could not archive {}: {}", url, wontRender.getMessage());
            return Optional.empty();
        } catch (Exception unreachable) {
            // The message is often null on a connection failure, so the type is what
            // says whether the browser is down or something else went wrong.
            LOGGER.warn("Could not reach the archiving browser at {} for {}: {}",
                    properties.browserUrl(), url, describe(unreachable));
            return Optional.empty();
        }
    }

    private static String describe(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }

    /**
     * The socket path carries an id only the browser knows, so it is asked rather
     * than assembled.
     */
    private String socketUrl(Duration timeout) throws Exception {
        HttpResponse<String> version = httpClient.send(
                HttpRequest.newBuilder(URI.create(properties.browserUrl() + "/json/version"))
                        .timeout(timeout)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return DevToolsProtocol.webSocketDebuggerUrl(version.body())
                .orElseThrow(() -> new DevToolsSession.DevToolsException(
                        properties.browserUrl() + " did not answer with a DevTools socket"));
    }

    /**
     * A tab of its own per archive, closed on every path out — a leaked one holds its
     * share of the browser's memory until the sidecar is restarted.
     */
    private byte[] print(DevToolsSession session, String url, Duration timeout) {
        String targetId = DevToolsProtocol.resultText(
                        session.call("Target.createTarget", Map.of("url", "about:blank"), null, timeout),
                        "targetId")
                .orElseThrow(() -> new DevToolsSession.DevToolsException("no target was created"));
        try {
            String pageSession = DevToolsProtocol.resultText(
                            session.call("Target.attachToTarget",
                                    Map.of("targetId", targetId, "flatten", true), null, timeout),
                            "sessionId")
                    .orElseThrow(() -> new DevToolsSession.DevToolsException("could not attach to the page"));
            return render(session, pageSession, url, timeout);
        } finally {
            closeQuietly(session, targetId, timeout);
        }
    }

    private byte[] render(DevToolsSession session, String pageSession, String url, Duration timeout) {
        session.call("Emulation.setDeviceMetricsOverride", Map.of(
                "width", properties.viewportWidth(),
                "height", 1024,
                "deviceScaleFactor", 1,
                "mobile", false), pageSession, timeout);
        // Screen media, not print: the archive should look like the page a reader saw,
        // and a print stylesheet is the site's instructions for paper instead.
        session.call("Emulation.setEmulatedMedia", Map.of("media", "screen"), pageSession, timeout);
        session.call("Page.enable", Map.of(), pageSession, timeout);

        // Registered before the navigation that provokes it, or a fast page finishes
        // loading before anyone is listening.
        CompletableFuture<String> loaded = session.expect("Page.loadEventFired");
        session.call("Page.navigate", Map.of("url", url), pageSession, timeout);
        session.await(loaded, "the page load", timeout);

        // What makes deferred images appear: the browser runs the script that reveals
        // them, which is the whole reason it is doing the rendering.
        session.call("Runtime.evaluate", Map.of(
                "expression", SCROLL_THROUGH,
                "awaitPromise", true), pageSession, timeout);
        settle();

        return DevToolsProtocol.printedPdf(session.call("Page.printToPDF", Map.of(
                        "printBackground", true,
                        "preferCSSPageSize", false), pageSession, timeout))
                .orElseThrow(() -> new DevToolsSession.DevToolsException("the browser printed nothing"));
    }

    private static void settle() {
        try {
            Thread.sleep(SETTLE.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A tab that will not close is worth a log line, never worth losing an archive
     * that has already been printed.
     */
    private static void closeQuietly(DevToolsSession session, String targetId, Duration timeout) {
        try {
            session.call("Target.closeTarget", Map.of("targetId", targetId), null, timeout);
        } catch (RuntimeException wontClose) {
            LOGGER.warn("Left a browser tab open: {}", wontClose.getMessage());
        }
    }
}
