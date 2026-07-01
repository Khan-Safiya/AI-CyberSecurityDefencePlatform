package com.cybersim.shared.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {
    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_ATTRIBUTE = CorrelationIds.class.getName() + ".value";

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private CorrelationIds() {
    }

    public static String acceptOrCreate(String candidate) {
        return candidate != null && SAFE_VALUE.matcher(candidate).matches() ? candidate : create();
    }

    public static String currentOrCreate() {
        String current = MDC.get(MDC_KEY);
        return current == null ? create() : current;
    }

    private static String create() {
        return UUID.randomUUID().toString();
    }
}
