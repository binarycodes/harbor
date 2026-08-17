package io.binarycodes.harbor.library.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Vets every address the fetcher is about to connect to. This sits at the resolver
 * rather than at the pasted URL for two reasons: the client connects to the
 * addresses handed back here, so there is no second lookup to disagree with the
 * check, and every redirect hop establishes its own route through this same
 * resolver, so following redirects stays safe without unpicking them by hand.
 */
@Component
public class GuardedDnsResolver implements DnsResolver {

    private final DnsResolver delegate;
    private final OutboundAddressPolicy policy;

    @Autowired
    public GuardedDnsResolver(OutboundAddressPolicy policy) {
        this(SystemDefaultDnsResolver.INSTANCE, policy);
    }

    GuardedDnsResolver(DnsResolver delegate, OutboundAddressPolicy policy) {
        this.delegate = delegate;
        this.policy = policy;
    }

    /**
     * Every answer has to pass, not merely one of them: a host that resolves to a
     * public address and a private one is trying to be fetched via the private one.
     *
     * <p>Returning an empty array is not an option — the interface's own
     * {@code resolve(host, port)} turns an empty answer into an unresolved address
     * for the connection to look up itself, which would sidestep this check
     * entirely. A refused address must throw.
     */
    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] resolved = delegate.resolve(host);
        if (resolved == null || resolved.length == 0) {
            throw new UnknownHostException(host);
        }
        for (InetAddress address : resolved) {
            if (!policy.permits(address)) {
                throw new BlockedAddressException(host, address.getHostAddress());
            }
        }
        return resolved;
    }

    /**
     * Left unguarded deliberately: the canonical name feeds host canonicalisation
     * for authentication schemes, never the address a connection is opened to. That
     * address always comes back through {@link #resolve(String)}.
     */
    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return delegate.resolveCanonicalHostname(host);
    }
}
