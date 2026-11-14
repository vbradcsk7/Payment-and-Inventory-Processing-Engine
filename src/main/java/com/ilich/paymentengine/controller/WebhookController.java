package com.ilich.paymentengine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilich.paymentengine.dto.request.PaymentWebhookEvent;
import com.ilich.paymentengine.dto.response.WebhookProcessResponse;
import com.ilich.paymentengine.security.CachedBodyHttpServletRequest;
import com.ilich.paymentengine.service.WebhookProcessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Endpoints for handling payment gateway asynchronous event notifications")
public class WebhookController {

    private final WebhookProcessorService webhookProcessorService;
    private final ObjectMapper objectMapper;

    public WebhookController(WebhookProcessorService webhookProcessorService, ObjectMapper objectMapper) {
        this.webhookProcessorService = webhookProcessorService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/payments")
    @Operation(
            summary = "Receive and process payment gateway webhook",
            description = "Secured via HMAC-SHA256 signature in X-Webhook-Signature header. " +
                    "Processes payment completion or failure idempotently and adjusts inventory accordingly."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhook received and processed idempotently"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid HMAC signature"),
            @ApiResponse(responseCode = "404", description = "Target order not found"),
            @ApiResponse(responseCode = "409", description = "Order state conflict")
    })
    public ResponseEntity<WebhookProcessResponse> handlePaymentWebhook(
            @Parameter(description = "HMAC-SHA256 hex signature of raw request body", required = true)
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @Valid @RequestBody PaymentWebhookEvent event,
            HttpServletRequest request) {

        String rawPayload;
        if (request instanceof CachedBodyHttpServletRequest cachedRequest) {
            rawPayload = new String(cachedRequest.getCachedBody(), StandardCharsets.UTF_8);
        } else {
            try {
                rawPayload = objectMapper.writeValueAsString(event);
            } catch (Exception e) {
                rawPayload = "{}";
            }
        }

        WebhookProcessResponse response = webhookProcessorService.processWebhook(event, rawPayload);
        return ResponseEntity.ok(response);
    }
}
