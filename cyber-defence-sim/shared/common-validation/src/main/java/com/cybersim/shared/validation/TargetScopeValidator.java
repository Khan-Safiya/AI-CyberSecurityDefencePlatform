package com.cybersim.shared.validation;

import com.cybersim.shared.dto.TargetMode;
import com.cybersim.shared.dto.TargetRegistrationRequest;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TargetScopeValidator {
    private static final Pattern HOST_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    private TargetScopeValidator() {
    }

    public static Optional<String> findViolation(TargetRegistrationRequest request) {
        URI baseUri;
        try {
            baseUri = new URI(request.baseUrl());
        } catch (URISyntaxException exception) {
            return Optional.of("base URL is invalid");
        }

        if (!isHttp(baseUri.getScheme()) || baseUri.getHost() == null) {
            return Optional.of("base URL must use HTTP or HTTPS and include a hostname");
        }
        if (baseUri.getRawUserInfo() != null) {
            return Optional.of("base URL must not contain embedded credentials");
        }
        if (baseUri.getRawFragment() != null || baseUri.getRawQuery() != null) {
            return Optional.of("base URL must not contain a query or fragment");
        }
        Optional<String> unsafeBasePath = findUnsafePath(List.of(baseUri.getRawPath()));
        if (unsafeBasePath.isPresent()) {
            return Optional.of("base URL " + unsafeBasePath.get());
        }

        HostPort baseHost;
        try {
            baseHost = new HostPort(normalizeHostname(baseUri.getHost()), effectivePort(baseUri));
        } catch (IllegalArgumentException exception) {
            return Optional.of("base URL hostname is invalid");
        }

        List<HostPort> allowedHosts;
        try {
            allowedHosts = request.allowedHosts().stream().map(TargetScopeValidator::parseAllowedHost).toList();
        } catch (IllegalArgumentException exception) {
            return Optional.of("allowed hosts must be exact hostnames with optional ports");
        }

        int defaultPort = "https".equalsIgnoreCase(baseUri.getScheme()) ? 443 : 80;
        if (allowedHosts.stream().noneMatch(allowed -> allowed.matches(baseHost, defaultPort))) {
            return Optional.of("base URL host and port must exactly match an allowed host");
        }

        if (request.mode() != TargetMode.INTERNAL_SANDBOX) {
            if (isNonPublicHost(baseHost.hostname())) {
                return Optional.of("external targets must not use local, private, reserved, or metadata hosts");
            }
            if (allowedHosts.stream().anyMatch(host -> isNonPublicHost(host.hostname()))) {
                return Optional.of("allowed hosts for external targets must be public hostnames");
            }
        }

        Optional<String> unsafePath = findUnsafePath(request.allowedPaths());
        if (unsafePath.isEmpty() && request.excludedPaths() != null) {
            unsafePath = findUnsafePath(request.excludedPaths());
        }
        if (unsafePath.isPresent()) {
            return unsafePath;
        }

        return Optional.empty();
    }

    private static boolean isHttp(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static HostPort parseAllowedHost(String value) {
        if (value.contains("/") || value.contains("@") || value.contains("?") || value.contains("#")) {
            throw new IllegalArgumentException("host contains URL syntax");
        }

        String hostname = value;
        int port = -1;
        int separator = value.lastIndexOf(':');
        if (separator >= 0) {
            hostname = value.substring(0, separator);
            try {
                port = Integer.parseInt(value.substring(separator + 1));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid port", exception);
            }
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("port out of range");
            }
        }

        String normalized = normalizeHostname(hostname);
        return new HostPort(normalized, port);
    }

    private static String normalizeHostname(String value) {
        String hostname = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        if (hostname.endsWith(".")) {
            hostname = hostname.substring(0, hostname.length() - 1);
        }
        if (hostname.isBlank() || hostname.length() > 253) {
            throw new IllegalArgumentException("invalid hostname length");
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

    private static Optional<String> findUnsafePath(List<String> paths) {
        for (String path : paths) {
            String lower = path.toLowerCase(Locale.ROOT);
            if (path.indexOf('\\') >= 0
                    || lower.contains("%2e")
                    || lower.contains("%2f")
                    || lower.contains("%5c")
                    || path.chars().anyMatch(Character::isISOControl)) {
                return Optional.of("scope paths must not contain traversal or encoded separator sequences");
            }
            for (String segment : path.split("/")) {
                if (segment.equals(".") || segment.equals("..")) {
                    return Optional.of("scope paths must not contain traversal segments");
                }
            }
        }
        return Optional.empty();
    }

    private record HostPort(String hostname, int port) {
        private boolean matches(HostPort baseHost, int defaultPort) {
            boolean portMatches = port < 0
                    ? baseHost.port == defaultPort
                    : port == baseHost.port;
            return hostname.equals(baseHost.hostname) && portMatches;
        }
    }
}
