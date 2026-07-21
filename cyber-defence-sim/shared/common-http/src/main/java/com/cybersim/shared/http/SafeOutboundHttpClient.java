package com.cybersim.shared.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * An outbound HTTP client for probes against target-supplied or user-supplied hosts (e.g. external
 * simulation targets). Every call re-resolves the hostname and rejects private/loopback/link-local/
 * multicast/reserved addresses immediately before connecting, never follows redirects automatically,
 * and caps both the connect/response timeout and the response body size.
 *
 * <p>Known limitation: resolution and connection are not atomic (the JDK HttpClient does not expose a
 * pinned-address connect hook), so a narrow DNS-rebinding window remains between the address check and
 * the actual TCP connect. This mirrors the level of protection already used elsewhere in this codebase
 * (see {@code WebhookDestinationValidator}) and is a known area for further hardening.
 */
public final class SafeOutboundHttpClient {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final long DEFAULT_MAX_RESPONSE_BYTES = 1_048_576L;

    private final HttpClient delegate;
    private final Duration timeout;
    private final long maxResponseBytes;
    private final boolean skipHostValidation;

    private SafeOutboundHttpClient(Duration timeout, long maxResponseBytes, boolean skipHostValidation) {
        this.timeout = timeout;
        this.maxResponseBytes = maxResponseBytes;
        this.skipHostValidation = skipHostValidation;
        this.delegate = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public static SafeOutboundHttpClient create() {
        return new SafeOutboundHttpClient(DEFAULT_TIMEOUT, DEFAULT_MAX_RESPONSE_BYTES, false);
    }

    public static SafeOutboundHttpClient create(Duration timeout, long maxResponseBytes) {
        return new SafeOutboundHttpClient(timeout, maxResponseBytes, false);
    }

    /** Test-only: bypasses host-safety validation so mechanics (timeout, redirect policy, size cap)
     * can be exercised against a loopback test server. Never call from production code. */
    static SafeOutboundHttpClient createForTesting(Duration timeout, long maxResponseBytes) {
        return new SafeOutboundHttpClient(timeout, maxResponseBytes, true);
    }

    public SafeHttpResponse send(SafeHttpRequest request) {
        URI uri = request.uri();
        if (!skipHostValidation) {
            validateDestination(uri);
        }
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .method(request.method(), bodyPublisher);
        request.headers().forEach(builder::header);
        try {
            HttpResponse<InputStream> response = delegate.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] body = readLimited(response.body(), maxResponseBytes);
            return new SafeHttpResponse(response.statusCode(), response.headers().map(), body);
        } catch (IOException exception) {
            throw new SafeOutboundHttpException("Outbound request to " + uri.getHost() + " failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SafeOutboundHttpException("Outbound request to " + uri.getHost() + " was interrupted", exception);
        }
    }

    private void validateDestination(URI uri) {
        var uriViolation = OutboundHostValidator.findUriViolation(uri);
        if (uriViolation.isPresent()) {
            throw new SafeOutboundHttpException("Refusing outbound request: " + uriViolation.get());
        }
        var hostnameViolation = OutboundHostValidator.findHostnameViolation(uri.getHost());
        if (hostnameViolation.isPresent()) {
            throw new SafeOutboundHttpException("Refusing outbound request: " + hostnameViolation.get());
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException exception) {
            throw new SafeOutboundHttpException("Refusing outbound request: cannot resolve host " + uri.getHost());
        }
        if (addresses.length == 0) {
            throw new SafeOutboundHttpException("Refusing outbound request: host did not resolve to any address");
        }
        for (InetAddress address : addresses) {
            if (OutboundHostValidator.isUnsafeAddress(address)) {
                throw new SafeOutboundHttpException(
                        "Refusing outbound request: " + uri.getHost() + " resolves to a disallowed address range");
            }
        }
    }

    private static byte[] readLimited(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new SafeOutboundHttpException("Response exceeded max allowed size of " + maxBytes + " bytes");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
