package com.cybersim.shared.http;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public record SafeHttpRequest(String method, URI uri, Map<String, String> headers) {
    public SafeHttpRequest {
        headers = Map.copyOf(headers);
    }

    public static SafeHttpRequest get(URI uri) {
        return new SafeHttpRequest("GET", uri, Map.of());
    }

    public static SafeHttpRequest get(URI uri, Map<String, String> headers) {
        return new SafeHttpRequest("GET", uri, headers);
    }

    public SafeHttpRequest withHeader(String name, String value) {
        Map<String, String> merged = new LinkedHashMap<>(headers);
        merged.put(name, value);
        return new SafeHttpRequest(method, uri, merged);
    }
}
