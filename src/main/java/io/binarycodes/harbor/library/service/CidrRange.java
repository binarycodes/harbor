package io.binarycodes.harbor.library.service;

import java.net.InetAddress;

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressNetwork;
import inet.ipaddr.IPAddressString;

/**
 * One range of addresses, written the way an operator writes it.
 *
 * <p>The matching itself is the IPAddress library's, which gets right the two things
 * Spring Security's {@code IpAddressMatcher} does not at the version this build
 * resolves: it never reports a match across address families, and it handles the IPv6
 * forms that carry an IPv4 address inside them — {@code ::169.254.169.254}, 6to4 and
 * Teredo all stay IPv6 addresses that a v4 rule must not be trusted to catch.
 *
 * <p>Two input checks are ours, because the library is more forgiving than an
 * operator's intent: it reads an empty string as loopback, and {@code 10/8} as
 * {@code 0.0.0.10/8} — a range that looks like all of 10.0.0.0/8 and matches almost
 * nothing.
 */
record CidrRange(IPAddress range) {

    private static final IPAddressNetwork.IPAddressGenerator ADDRESS_GENERATOR =
            new IPAddressNetwork.IPAddressGenerator();

    static CidrRange of(String notation) {
        String trimmed = notation == null ? "" : notation.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("an address range cannot be blank");
        }
        rejectAbbreviatedIpv4(trimmed);
        IPAddressString parsed = new IPAddressString(trimmed);
        try {
            parsed.validate();
        } catch (AddressStringException notAnAddress) {
            throw new IllegalArgumentException(
                    "\"%s\" is not an address range: %s".formatted(trimmed, notAnAddress.getMessage()),
                    notAnAddress);
        }
        return new CidrRange(parsed.getAddress());
    }

    boolean contains(InetAddress candidate) {
        return range.contains(ADDRESS_GENERATOR.from(candidate));
    }

    /**
     * A dotted form has to carry all four parts. Without this, {@code 10/8} parses
     * happily into a range nobody meant to write.
     */
    private static void rejectAbbreviatedIpv4(String notation) {
        String address = notation.split("/", -1)[0];
        boolean dottedOrNumeric = address.chars()
                .allMatch(character -> Character.isDigit(character) || character == '.');
        if (dottedOrNumeric && address.chars().filter(character -> character == '.').count() != 3) {
            throw new IllegalArgumentException(
                    "\"%s\" is not a full IPv4 address — write all four parts".formatted(notation));
        }
    }
}
