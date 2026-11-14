package com.ilich.paymentengine.dto.response;

import com.ilich.paymentengine.model.enums.WebhookEventStatus;
import java.time.Instant;

public record WebhookProcessResponse(
        String eventId,
        WebhookEventStatus status,
        String message,
        Instant processedAt
) {}
