package io.binarycodes.harbor.library.service;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

/**
 * The one client anything outbound goes through. Its DNS resolution is vetted, so
 * a pasted link can only reach an address the deployment permits — including
 * across redirects, since each hop opens its own connection through the same
 * resolver.
 *
 * <p>Shared rather than built per caller on purpose: the guard is only worth
 * anything if there is no second way out. A fetcher that built its own client
 * would quietly be an unguarded one.
 */
@Component
class GuardedHttpClient {

    static final String USER_AGENT =
            "Mozilla/5.0 (compatible; Harbor/1.0; +local-first bookmark manager)";

    private final CloseableHttpClient httpClient;

    GuardedHttpClient(GuardedDnsResolver dnsResolver, OutboundFetchProperties properties) {
        Timeout timeout = Timeout.ofMilliseconds(properties.timeout().toMillis());
        this.httpClient = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dnsResolver)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(timeout)
                                .setSocketTimeout(timeout)
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setRedirectsEnabled(true)
                        .setCircularRedirectsAllowed(false)
                        .setMaxRedirects(properties.maxRedirects())
                        .setConnectionRequestTimeout(timeout)
                        .setResponseTimeout(timeout)
                        .build())
                .disableCookieManagement()
                .disableAuthCaching()
                .disableAutomaticRetries()
                .build();
    }

    CloseableHttpClient client() {
        return httpClient;
    }
}
