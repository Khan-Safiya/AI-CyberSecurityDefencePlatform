package com.cybersim.shared.observability;

import com.cybersim.shared.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ApiErrors {
    private ApiErrors() {
    }

    public static ResponseEntity<Object> response(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        path,
                        CorrelationIds.currentOrCreate()
                ));
    }
}
