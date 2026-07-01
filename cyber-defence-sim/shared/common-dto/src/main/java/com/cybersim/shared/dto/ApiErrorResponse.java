package com.cybersim.shared.dto;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId
) {
    public static ApiErrorResponse of(int status, String error, String message, String path, String correlationId) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, correlationId);
    }
}
