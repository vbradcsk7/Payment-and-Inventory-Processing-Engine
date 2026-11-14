package com.ilich.paymentengine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilich.paymentengine.config.FilterConfig;
import com.ilich.paymentengine.dto.request.PaymentWebhookEvent;
import com.ilich.paymentengine.dto.response.WebhookProcessResponse;
import com.ilich.paymentengine.model.enums.WebhookEventStatus;
import com.ilich.paymentengine.security.HmacSignatureValidator;
import com.ilich.paymentengine.service.WebhookProcessorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WebhookController.class)
@Import({FilterConfig.class, HmacSignatureValidator.class})
@TestPropertySource(properties = "payment.webhook.secret=test_secret_key_12345")
class WebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HmacSignatureValidator signatureValidator;

    @MockBean
    private WebhookProcessorService webhookProcessorService;

    @Test
    @DisplayName("Should accept webhook when valid HMAC-SHA256 signature is provided")
    void shouldAcceptWebhookWithValidSignature() throws Exception {
        UUID orderId = UUID.randomUUID();
        PaymentWebhookEvent event = new PaymentWebhookEvent(
                "evt_valid_1", "STRIPE", "payment.succeeded", orderId, BigDecimal.valueOf(100.00), "USD"
        );
        String payloadJson = objectMapper.writeValueAsString(event);
        String signature = signatureValidator.calculateHmac(payloadJson.getBytes(StandardCharsets.UTF_8));

        when(webhookProcessorService.processWebhook(any(PaymentWebhookEvent.class), anyString()))
                .thenReturn(new WebhookProcessResponse("evt_valid_1", WebhookEventStatus.PROCESSED, "Success", Instant.now()));

        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", signature)
                        .content(payloadJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt_valid_1"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    @DisplayName("Should reject webhook with 401 when X-Webhook-Signature header is missing")
    void shouldRejectWhenSignatureHeaderMissing() throws Exception {
        PaymentWebhookEvent event = new PaymentWebhookEvent(
                "evt_no_sig", "STRIPE", "payment.succeeded", UUID.randomUUID(), BigDecimal.valueOf(100.00), "USD"
        );
        String payloadJson = objectMapper.writeValueAsString(event);

        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Missing 'X-Webhook-Signature' header"));
    }

    @Test
    @DisplayName("Should reject webhook with 401 when HMAC signature is forged/invalid")
    void shouldRejectForgedSignature() throws Exception {
        PaymentWebhookEvent event = new PaymentWebhookEvent(
                "evt_forged", "STRIPE", "payment.succeeded", UUID.randomUUID(), BigDecimal.valueOf(100.00), "USD"
        );
        String payloadJson = objectMapper.writeValueAsString(event);

        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "invalid_forged_hex_signature_abcdef123456")
                        .content(payloadJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid webhook HMAC signature"));
    }
}
