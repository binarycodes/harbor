package io.binarycodes.harbor.library.service;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decides whether the server is allowed to fetch from an address. Saving a link makes
 * the server issue the request, so without this a pasted URL reaches whatever the
 * server can reach — cloud metadata, the local network, a neighbouring container —
 * and the fetched page comes back rendered into the reader.
 *
 * <p>The reserved ranges are refused by default and an allowed range overrides them,
 * so a deployment whose own hosts are private says so in one line instead of
 * restating everything it still wants refused.
 */
@Component
public class OutboundAddressPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboundAddressPolicy.class);

    private final List<CidrRange> blocked;
    private final List<CidrRange> allowed;

    /**
     * Ranges are parsed here rather than per request so that a mistyped one fails the
     * deployment at startup instead of silently never matching.
     */
    public OutboundAddressPolicy(OutboundFetchProperties properties) {
        this.blocked = parse(properties.blockedRanges(), "harbor.fetch.blocked-ranges");
        this.allowed = parse(properties.allowedRanges(), "harbor.fetch.allowed-ranges");
        if (blocked.isEmpty()) {
            LOGGER.warn("harbor.fetch.blocked-ranges is empty, so the server will fetch any address a "
                    + "visitor pastes, including this host's own network");
        }
        if (!allowed.isEmpty()) {
            LOGGER.info("harbor.fetch.allowed-ranges permits {} range(s) that would otherwise be refused",
                    allowed.size());
        }
    }

    public boolean permits(InetAddress address) {
        return contains(allowed, address) || !contains(blocked, address);
    }

    private static boolean contains(List<CidrRange> ranges, InetAddress address) {
        return ranges.stream().anyMatch(range -> range.contains(address));
    }

    /**
     * Every entry is parsed before anything is reported, so an operator with three
     * typos sees three of them rather than one per restart.
     */
    private static List<CidrRange> parse(List<String> notations, String property) {
        List<CidrRange> parsed = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (String notation : notations) {
            try {
                parsed.add(CidrRange.of(notation));
            } catch (IllegalArgumentException unreadable) {
                problems.add(unreadable.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("%s cannot be read: %s. Write a full IPv4 dotted quad or an IPv6 "
                    .formatted(property, String.join("; ", problems))
                    + "literal with an optional /prefix — for example 192.168.1.50/32, 10.0.0.0/8 or fd00::/8");
        }
        return List.copyOf(parsed);
    }
}
