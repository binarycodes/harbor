package io.binarycodes.harbor.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Deciding where the server may fetch from")
class OutboundAddressPolicyTest {

    private static final List<String> DEFAULT_BLOCKED = ReservedAddressRanges.notations();

    @Nested
    @DisplayName("with the shipped defaults")
    class ShippedDefaults {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "127.0.0.1",
                "10.0.0.1",
                "192.168.1.5",
                "172.16.0.1",
                "169.254.169.254",
                "100.64.0.1",
                "0.0.0.0",
                "255.255.255.255",
                "224.0.0.1",
                "240.0.0.1"
        })
        @DisplayName("refuses the IPv4 addresses no pasted link has any business reaching")
        void refusesPrivateAndBogusIpv4(String address) throws UnknownHostException {
            assertFalse(defaultPolicy().permits(InetAddress.getByName(address)));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "::1",
                "::",
                "fc00::1",
                "fd12:3456:789a::1",
                "fe80::1ff:fe23:4567:890a",
                "ff02::1"
        })
        @DisplayName("refuses the IPv6 equivalents")
        void refusesPrivateAndBogusIpv6(String address) throws UnknownHostException {
            assertFalse(defaultPolicy().permits(InetAddress.getByName(address)));
        }

        /**
         * The forms that smuggle an IPv4 address inside an IPv6 one. Only the mapped
         * form is normalised to IPv4 by the JDK; {@code ::169.254.169.254}, 6to4 and
         * Teredo stay IPv6 addresses, so each needs its own range to catch it.
         */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "::ffff:127.0.0.1",
                "::ffff:192.168.1.5",
                "::ffff:169.254.169.254",
                "::169.254.169.254",
                "64:ff9b::c0a8:105",
                "2002:c0a8:0101::1",
                "2001:0:c0a8:0101::1"
        })
        @DisplayName("refuses a private address smuggled inside an IPv6 one")
        void refusesEmbeddedIpv4Addresses(String address) throws UnknownHostException {
            assertFalse(defaultPolicy().permits(InetAddress.getByName(address)));
        }

        /**
         * A range only ever matches its own family. Spring Security's matcher reports
         * {@code 0.0.0.0/8} as containing {@code ::1} and throws when a 16-byte range
         * meets a 4-byte address, which is why the matching is not built on it.
         */
        @Test
        @DisplayName("never matches across address families")
        void doesNotMatchAcrossFamilies() throws UnknownHostException {
            assertFalse(CidrRange.of("0.0.0.0/8").contains(InetAddress.getByName("::1")));
            assertFalse(CidrRange.of("::/96").contains(InetAddress.getByName("0.0.0.0")));
            assertFalse(CidrRange.of("255.255.255.255/32")
                    .contains(InetAddress.getByName("2606:4700:4700::1111")));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "2606:4700:4700::1111"})
        @DisplayName("permits the public internet")
        void permitsPublicAddresses(String address) throws UnknownHostException {
            assertTrue(defaultPolicy().permits(InetAddress.getByName(address)));
        }
    }

    @Nested
    @DisplayName("when an operator widens it")
    class OperatorOverride {

        @Test
        @DisplayName("an allowed range wins over the blocked defaults")
        void allowedRangeWins() throws UnknownHostException {
            OutboundAddressPolicy policy = policy(DEFAULT_BLOCKED, List.of("192.168.1.50/32"));

            assertTrue(policy.permits(InetAddress.getByName("192.168.1.50")));
        }

        @Test
        @DisplayName("and widens no further than it says")
        void allowedRangeIsExact() throws UnknownHostException {
            OutboundAddressPolicy policy = policy(DEFAULT_BLOCKED, List.of("192.168.1.50/32"));

            assertFalse(policy.permits(InetAddress.getByName("192.168.1.51")));
            assertFalse(policy.permits(InetAddress.getByName("169.254.169.254")));
        }

        @Test
        @DisplayName("reaches loopback when asked to, for a deployment that wants it")
        void allowsLoopbackWhenAsked() throws UnknownHostException {
            OutboundAddressPolicy policy = policy(DEFAULT_BLOCKED, List.of("127.0.0.0/8"));

            assertTrue(policy.permits(InetAddress.getByName("127.0.0.1")));
        }

        @Test
        @DisplayName("an IPv6 range works the same way")
        void allowedIpv6Range() throws UnknownHostException {
            OutboundAddressPolicy policy = policy(DEFAULT_BLOCKED, List.of("fc00::/7"));

            assertTrue(policy.permits(InetAddress.getByName("fd12:3456::1")));
        }
    }

    @Nested
    @DisplayName("when a range is mistyped")
    class MalformedRanges {

        @Test
        @DisplayName("the deployment fails to start rather than quietly never matching")
        void refusesAnImpossiblePrefix() {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> policy(List.of("192.168.0.0/33"), List.of()));

            assertTrue(failure.getMessage().contains("192.168.0.0"));
        }

        @Test
        @DisplayName("and says so for something that is not an address at all")
        void refusesNonsense() {
            assertThrows(IllegalArgumentException.class,
                    () -> policy(List.of("garbage"), List.of()));
        }

        @Test
        @DisplayName("including in the allowed list")
        void refusesNonsenseInAllowed() {
            assertThrows(IllegalArgumentException.class,
                    () -> policy(List.of(), List.of("192.168.1.0/nope")));
        }

        /**
         * The library reads both of these without complaint — an empty string as
         * loopback, and 10/8 as 0.0.0.10/8 — so Harbor rejects them itself rather than
         * installing a rule the operator did not write.
         */
        @Test
        @DisplayName("refuses an abbreviated IPv4 range that would mean something else")
        void refusesAbbreviatedIpv4() {
            assertThrows(IllegalArgumentException.class, () -> CidrRange.of("10/8"));
            assertThrows(IllegalArgumentException.class, () -> CidrRange.of(""));
            assertThrows(IllegalArgumentException.class, () -> CidrRange.of("   "));
        }

        @Test
        @DisplayName("names every bad range at once, not one per restart")
        void reportsEveryProblem() {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> policy(List.of("garbage", "10/8"), List.of()));

            assertTrue(failure.getMessage().contains("garbage"));
            assertTrue(failure.getMessage().contains("10/8"));
            assertTrue(failure.getMessage().contains("harbor.fetch.blocked-ranges"));
        }
    }

    @Nested
    @DisplayName("the properties themselves")
    class Defaults {

        @Test
        @DisplayName("fall back to workable values when nothing is configured")
        void fillInMissingValues() {
            OutboundFetchProperties properties =
                    new OutboundFetchProperties(null, null, null, 0, 0);

            assertEquals(ReservedAddressRanges.notations(), properties.blockedRanges());
            assertEquals(List.of(), properties.allowedRanges());
            assertEquals(Duration.ofSeconds(8), properties.timeout());
            assertEquals(5, properties.maxRedirects());
            assertEquals(512 * 1024, properties.maxBodyBytes());
        }
    }

    private static OutboundAddressPolicy defaultPolicy() {
        return policy(DEFAULT_BLOCKED, List.of());
    }

    private static OutboundAddressPolicy policy(List<String> blocked, List<String> allowed) {
        return new OutboundAddressPolicy(
                new OutboundFetchProperties(blocked, allowed, Duration.ofSeconds(8), 5, 512 * 1024));
    }
}
