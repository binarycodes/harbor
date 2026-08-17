package io.binarycodes.harbor.library.service;

import java.util.List;

/**
 * The address space a web page cannot legitimately live in: the private, loopback,
 * link-local and reserved blocks, plus the IPv6 prefixes that carry an IPv4 address
 * inside them. Saving a link makes the server fetch it, so this is the list that
 * stops a pasted URL from reaching the network the server sits on.
 *
 * <p>A comment per entry rather than none: which RFC a range comes from is not
 * recoverable from the numbers, and the next person to touch this list needs to know
 * whether an entry can be dropped.
 */
final class ReservedAddressRanges {

    private static final List<String> NOTATIONS = List.of(
            "0.0.0.0/8",          // RFC 1122 "this network" — http://0/ reaches loopback on Linux
            "10.0.0.0/8",         // RFC 1918 private
            "100.64.0.0/10",      // RFC 6598 carrier-grade NAT, routable inside ISP and cloud fabrics
            "127.0.0.0/8",        // RFC 1122 loopback — Harbor's own port and anything else bound locally
            "169.254.0.0/16",     // RFC 3927 link-local, and so 169.254.169.254, the cloud metadata endpoint
            "172.16.0.0/12",      // RFC 1918 private, and the default Docker bridge space
            "192.0.0.0/24",       // RFC 6890 IETF protocol assignments
            "192.0.2.0/24",       // RFC 5737 documentation
            "192.88.99.0/24",     // RFC 7526 deprecated 6to4 relay anycast
            "192.168.0.0/16",     // RFC 1918 private — the usual home LAN
            "198.18.0.0/15",      // RFC 2544 benchmarking
            "198.51.100.0/24",    // RFC 5737 documentation
            "203.0.113.0/24",     // RFC 5737 documentation
            "224.0.0.0/4",        // RFC 5771 multicast — never a page
            "240.0.0.0/4",        // RFC 1112 reserved, and it covers the 255.255.255.255 broadcast

            "::/128",             // the unspecified address
            "::1/128",            // loopback
            "::/96",              // RFC 4291 IPv4-compatible — ::169.254.169.254 stays an IPv6 address
            "::ffff:0:0/96",      // IPv4-mapped; the JDK normalises these to IPv4, so this is belt and braces
            "64:ff9b::/96",       // RFC 6052 NAT64 well-known prefix, embeds any IPv4 address
            "64:ff9b:1::/48",     // RFC 8215 local-use NAT64
            "100::/64",           // RFC 6666 discard-only
            "2001::/32",          // RFC 4380 Teredo, embeds an IPv4 address
            "2001:db8::/32",      // RFC 3849 documentation
            "2002::/16",          // RFC 3056 6to4, embeds an IPv4 address
            "fc00::/7",           // RFC 4193 unique-local, covering fc00::/8 and fd00::/8
            "fe80::/10",          // RFC 4291 link-local
            "fec0::/10",          // RFC 3879 deprecated site-local
            "ff00::/8");          // RFC 4291 multicast

    private ReservedAddressRanges() {
    }

    static List<String> notations() {
        return NOTATIONS;
    }
}
