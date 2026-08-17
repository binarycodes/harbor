package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;

import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Vetting an address before the fetcher connects to it")
class GuardedDnsResolverTest {

    @Test
    @DisplayName("hands back a public answer untouched")
    void passesPublicAddresses() throws UnknownHostException {
        InetAddress[] answer = addresses("8.8.8.8", "1.1.1.1");

        assertArrayEquals(answer, resolverAnswering(answer).resolve("dns.example.com"));
    }

    @Test
    @DisplayName("refuses a private answer, naming the address it refused")
    void refusesPrivateAddresses() throws UnknownHostException {
        GuardedDnsResolver resolver = resolverAnswering(addresses("169.254.169.254"));

        BlockedAddressException blocked = assertThrows(BlockedAddressException.class,
                () -> resolver.resolve("metadata.example.com"));

        assertEquals("169.254.169.254", blocked.getAddress());
    }

    /**
     * A host answering with one public address and one private one is asking to be
     * reached on the private one, so the whole answer goes.
     */
    @Test
    @DisplayName("refuses a mixed answer rather than picking the public half")
    void refusesMixedAnswers() throws UnknownHostException {
        GuardedDnsResolver resolver = resolverAnswering(addresses("8.8.8.8", "192.168.1.5"));

        BlockedAddressException blocked = assertThrows(BlockedAddressException.class,
                () -> resolver.resolve("split.example.com"));

        assertEquals("192.168.1.5", blocked.getAddress());
    }

    /**
     * Returning nothing would have the client fall back to resolving the host itself,
     * which is the one outcome that would sidestep this check.
     */
    @Test
    @DisplayName("throws rather than returning an empty answer")
    void refusesEmptyAnswers() {
        GuardedDnsResolver resolver = resolverAnswering(new InetAddress[0]);

        assertThrows(UnknownHostException.class, () -> resolver.resolve("nothing.example.com"));
    }

    @Test
    @DisplayName("lets an operator-permitted private address through")
    void honoursAllowedRanges() throws UnknownHostException {
        InetAddress[] answer = addresses("192.168.1.50");
        GuardedDnsResolver resolver = new GuardedDnsResolver(
                stub(answer), policy(List.of("192.168.1.50/32")));

        assertArrayEquals(answer, resolver.resolve("nas.example.com"));
    }

    private static GuardedDnsResolver resolverAnswering(InetAddress[] answer) {
        return new GuardedDnsResolver(stub(answer), policy(List.of()));
    }

    private static DnsResolver stub(InetAddress[] answer) {
        return new DnsResolver() {

            @Override
            public InetAddress[] resolve(String host) {
                return answer;
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
    }

    private static OutboundAddressPolicy policy(List<String> allowed) {
        return new OutboundAddressPolicy(new OutboundFetchProperties(
                ReservedAddressRanges.notations(), allowed, Duration.ofSeconds(8), 5, 512 * 1024));
    }

    private static InetAddress[] addresses(String... literals) throws UnknownHostException {
        InetAddress[] resolved = new InetAddress[literals.length];
        for (int index = 0; index < literals.length; index++) {
            resolved[index] = InetAddress.getByName(literals[index]);
        }
        return resolved;
    }
}
