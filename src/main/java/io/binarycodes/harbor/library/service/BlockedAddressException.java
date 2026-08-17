package io.binarycodes.harbor.library.service;

import java.net.UnknownHostException;

/**
 * Raised when a pasted link resolves somewhere the deployment does not allow the
 * server to reach. It extends {@link UnknownHostException} because that is
 * the only failure a {@code DnsResolver} may report, but it is caught by name so
 * that a refused address can be told apart from a host that genuinely does not
 * resolve — the first is worth explaining to whoever pasted the link, the second
 * just falls back to describing it from its URL.
 */
public class BlockedAddressException extends UnknownHostException {

    private final String address;

    public BlockedAddressException(String host, String address) {
        super("%s resolves to %s, which this deployment does not allow the server to fetch".formatted(host, address));
        this.address = address;
    }

    public String getAddress() {
        return address;
    }
}
