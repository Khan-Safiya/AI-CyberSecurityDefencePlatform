package com.cybersim.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebhookDeliveryRequest(
        @NotBlank
        @Size(max = 120)
        String event,
        @NotBlank
        @Size(max = 2048)
        String destinationUrl
) {
}
