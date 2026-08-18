package io.binarycodes.harbor.library.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Fetches a page through a client whose DNS resolution is vetted, so a pasted link
 * can only reach an address the deployment permits — including across redirects,
 * since each hop opens its own connection through the same resolver.
 *
 * <p>Apache's client replaces jsoup's own fetch purely for that hook; jsoup still
 * does the parsing, so everything downstream sees the same {@link Document} it
 * always did. The response limits jsoup applied for free are reimposed here: refuse
 * anything that is not HTML, stop reading at a fixed size, and treat an error status
 * as a failure to read the page.
 *
 * <p>The client itself is {@link GuardedHttpClient}'s, shared with everything else
 * that reaches out.
 */
@Component
class HttpDocumentLoader implements DocumentLoader {

    private static final Set<String> HTML_MIME_TYPES =
            Set.of("text/html", "application/xhtml+xml");
    private static final int ERROR_STATUS_FLOOR = 400;

    private final GuardedHttpClient httpClient;
    private final int maxBodyBytes;

    HttpDocumentLoader(GuardedHttpClient httpClient, OutboundFetchProperties properties) {
        this.httpClient = httpClient;
        this.maxBodyBytes = properties.maxBodyBytes();
    }

    @Override
    public Document load(String url) throws IOException {
        HttpGet request = new HttpGet(url);
        request.setHeader("User-Agent", GuardedHttpClient.USER_AGENT);
        request.setHeader("Accept", "text/html,application/xhtml+xml");

        HttpClientContext context = HttpClientContext.create();
        try {
            String html = httpClient.client().execute(request, context, response -> {
                if (response.getCode() >= ERROR_STATUS_FLOOR) {
                    throw new IOException("%s answered %d".formatted(url, response.getCode()));
                }
                return readHtml(response.getEntity(), url);
            });
            return Jsoup.parse(html, finalUrl(url, context));
        } catch (IOException failure) {
            throw refusalWithin(failure);
        }
    }

    /**
     * A refusal has to arrive as itself, not as whatever the client wrapped it in, or
     * the caller cannot tell a blocked address from an unreachable host. It currently
     * propagates unwrapped; walking the chain keeps that true across upgrades.
     */
    private static IOException refusalWithin(IOException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof BlockedAddressException blocked) {
                return blocked;
            }
        }
        return failure;
    }

    /**
     * The document's base URI has to be where the content actually came from, or
     * every relative link the extractor resolves points at the wrong host.
     */
    private static String finalUrl(String requested, HttpClientContext context) {
        List<URI> redirects = context.getRedirectLocations() == null
                ? List.of()
                : context.getRedirectLocations().getAll();
        return redirects.isEmpty() ? requested : redirects.getLast().toString();
    }

    private String readHtml(HttpEntity entity, String url) throws IOException {
        if (entity == null) {
            throw new IOException("%s answered with no body".formatted(url));
        }
        ContentType contentType = ContentType.parseLenient(entity.getContentType());
        if (contentType != null && !HTML_MIME_TYPES.contains(contentType.getMimeType())) {
            throw new IOException("%s served %s, which is not a page".formatted(url, contentType.getMimeType()));
        }
        Charset charset = contentType == null || contentType.getCharset() == null
                ? StandardCharsets.UTF_8
                : contentType.getCharset();
        try (InputStream body = entity.getContent()) {
            return new String(body.readNBytes(maxBodyBytes), charset);
        }
    }
}
