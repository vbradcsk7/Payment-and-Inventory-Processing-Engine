package com.ilich.paymentengine.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentWebhookEvent(
        @NotBlank(message = "Event ID is required")
        String eventId,

        @NotBlank(message = "Provider is required")
        String provider,

        @NotBlank(message = "Event type is required")
        String eventType,

        @NotNull(message = "Order ID is required")
        UUID orderId,

        BigDecimal amount,
        String currency
) {}
