package io.binarycodes.harbor.library.service;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How far the server may reach when it fetches a pasted link, and how much it will
 * read once it gets there. Saving a link is the one place Harbor makes an outbound
 * request on a visitor's behalf, so the reachable address space is deployment
 * configuration rather than a constant.
 *
 * @param blockedRanges ranges refused, in CIDR notation; defaults to
 *                      {@link ReservedAddressRanges}, and configuring it replaces
 *                      that list rather than adding to it
 * @param allowedRanges ranges permitted regardless of every other rule — the single
 *                      escape hatch for a deployment whose own hosts are private
 * @param timeout       how long to wait on the host before giving up
 * @param maxRedirects  how many hops to follow; each one is vetted afresh
 * @param maxBodyBytes  how much of the response to read before truncating
 */
@ConfigurationProperties("harbor.fetch")
public record OutboundFetchProperties(
        List<String> blockedRanges,
        List<String> allowedRanges,
        Duration timeout,
        int maxRedirects,
        int maxBodyBytes) {

    public OutboundFetchProperties {
        blockedRanges = blockedRanges == null
                ? ReservedAddressRanges.notations()
                : List.copyOf(blockedRanges);
        allowedRanges = allowedRanges == null ? List.of() : List.copyOf(allowedRanges);
        timeout = timeout == null ? Duration.ofSeconds(8) : timeout;
        maxRedirects = maxRedirects <= 0 ? 5 : maxRedirects;
        maxBodyBytes = maxBodyBytes <= 0 ? 512 * 1024 : maxBodyBytes;
    }
}
