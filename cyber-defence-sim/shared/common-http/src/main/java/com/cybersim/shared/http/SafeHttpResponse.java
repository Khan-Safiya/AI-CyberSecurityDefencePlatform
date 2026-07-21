package com.cybersim.shared.http;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record SafeHttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public boolean hasHeader(String name) {
        return headers.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }

    public boolean bodyContainsIgnoreCase(String needle) {
        return bodyAsString().toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
