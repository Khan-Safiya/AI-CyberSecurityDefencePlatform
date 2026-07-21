package com.cybersim.notificationservice.webhook;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class WebhookDestinationValidator {
    private static final Pattern HOST_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    private WebhookDestinationValidator() {
    }

    public static Optional<String> findViolation(String destinationUrl) {
        URI uri;
        try {
            uri = new URI(destinationUrl);
        } catch (URISyntaxException exception) {
            return Optional.of("destination URL is invalid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return Optional.of("destination URL must use HTTPS and include a hostname");
        }
        if (uri.getRawUserInfo() != null) {
            return Optional.of("destination URL must not contain embedded credentials");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return Optional.of("destination URL must not contain a query or fragment");
        }
        if (uri.getPort() > 65_535) {
            return Optional.of("destination URL port is invalid");
        }

        String host;
        try {
            host = normalizeHostname(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return Optional.of("destination URL hostname is invalid");
        }
        if (isNonPublicHost(host)) {
            return Optional.of("destination URL must use a public hostname");
        }
        return Optional.empty();
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

    private static boolean isNonPublicHost(String hostname) {
        String lower = hostname.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost")
                || lower.endsWith(".localhost")
                || lower.endsWith(".local")
                || lower.endsWith(".internal")
                || lower.endsWith(".home.arpa")
                || lower.equals("metadata.google.internal")
                || !lower.contains(".")) {
            return true;
        }
        int[] address = parseIpv4(lower);
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
}
