package com.cybersim.shared.http;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Shared safety checks for any outbound request this platform makes to a target-supplied
 * or user-supplied URL. Two independent checks are exposed: a syntactic check on the URI/hostname
 * (no network access), and an address-range check on a resolved {@link InetAddress} (requires the
 * caller to have already resolved the hostname). Callers must apply the address check to every
 * address returned by resolution, immediately before connecting, to minimize (not eliminate) the
 * DNS-rebinding window between validation and connect.
 */
public final class OutboundHostValidator {
    private static final Pattern HOST_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    private OutboundHostValidator() {
    }

    public static Optional<String> findUriViolation(URI uri) {
        if (uri == null) {
            return Optional.of("URI must not be null");
        }
        if (!isHttp(uri.getScheme()) || uri.getHost() == null) {
            return Optional.of("URI must use HTTP or HTTPS and include a hostname");
        }
        if (uri.getRawUserInfo() != null) {
            return Optional.of("URI must not contain embedded credentials");
        }
        if (uri.getPort() > 65_535) {
            return Optional.of("URI port is invalid");
        }
        try {
            normalizeHostname(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return Optional.of("URI hostname is invalid");
        }
        return Optional.empty();
    }

    public static Optional<String> findHostnameViolation(String hostname) {
        String normalized;
        try {
            normalized = normalizeHostname(hostname);
        } catch (IllegalArgumentException exception) {
            return Optional.of("hostname is invalid");
        }
        if (isNonPublicHostname(normalized)) {
            return Optional.of("hostname must be a public hostname");
        }
        return Optional.empty();
    }

    public static boolean isUnsafeAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress() || address.isAnyLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            return first == 0
                    || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0 && (bytes[2] & 0xFF) == 0)
                    || (first == 198 && (second == 18 || second == 19));
        }
        if (bytes.length == 16) {
            int first = bytes[0] & 0xFF;
            // Unique local addresses (fc00::/7) - covers fd00::/8 used by most private IPv6 setups.
            return (first & 0xFE) == 0xFC;
        }
        return false;
    }

    private static boolean isHttp(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static String normalizeHostname(String value) {
        String hostname = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        if (hostname.endsWith(".")) {
            hostname = hostname.substring(0, hostname.length() - 1);
        }
        if (hostname.isBlank() || hostname.length() > 253 || hostname.contains(":")) {
            throw new IllegalArgumentException("invalid hostname");
        }
        int[] ipv4 = parseIpv4(hostname);
        if (ipv4 == null) {
            if (hostname.chars().allMatch(character -> Character.isDigit(character) || character == '.')) {
                throw new IllegalArgumentException("invalid IPv4 address");
            }
            for (String label : hostname.split("\\.")) {
                if (!HOST_LABEL.matcher(label).matches()) {
                    throw new IllegalArgumentException("invalid hostname label");
                }
            }
        }
        return hostname;
    }

    private static boolean isNonPublicHostname(String hostname) {
        if (hostname.equals("localhost")
                || hostname.endsWith(".localhost")
                || hostname.endsWith(".local")
                || hostname.endsWith(".internal")
                || hostname.endsWith(".home.arpa")
                || hostname.equals("metadata.google.internal")
                || !hostname.contains(".")) {
            return true;
        }
        int[] address = parseIpv4(hostname);
        if (address == null) {
            return false;
        }
        int first = address[0];
        int second = address[1];
        int third = address[2];
        return first == 0
                || first == 10
                || first == 127
                || first >= 224
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 192 && second == 0 && third == 0)
                || (first == 198 && (second == 18 || second == 19));
    }

    private static int[] parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] address = new int[4];
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].isEmpty()
                    || parts[index].length() > 1 && parts[index].startsWith("0")
                    || !parts[index].chars().allMatch(Character::isDigit)) {
                return null;
            }
            try {
                address[index] = Integer.parseInt(parts[index]);
            } catch (NumberFormatException exception) {
                return null;
            }
            if (address[index] > 255) {
                return null;
            }
        }
        return address;
    }

    static URI requireValidUri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("invalid URI: " + value, exception);
        }
    }
}
