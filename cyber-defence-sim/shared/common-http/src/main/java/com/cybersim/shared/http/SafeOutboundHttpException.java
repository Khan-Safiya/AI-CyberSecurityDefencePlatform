package com.cybersim.shared.http;

public class SafeOutboundHttpException extends RuntimeException {
    public SafeOutboundHttpException(String message) {
        super(message);
    }

    public SafeOutboundHttpException(String message, Throwable cause) {
        super(message, cause);
    }
}
