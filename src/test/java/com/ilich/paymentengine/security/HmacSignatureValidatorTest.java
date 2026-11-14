package com.ilich.paymentengine.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignatureValidatorTest {

    private static final String TEST_SECRET = "super_secret_webhook_key_987654";
    private HmacSignatureValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HmacSignatureValidator(TEST_SECRET);
    }

    @Test
    @DisplayName("Should successfully validate a valid HMAC-SHA256 signature")
    void shouldValidateValidSignature() {
        String payload = "{\"eventId\":\"evt_100\",\"amount\":99.99}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        String signature = validator.calculateHmac(payloadBytes);

        assertThat(validator.isValidSignature(payloadBytes, signature)).isTrue();
    }

    @Test
    @DisplayName("Should validate signature when prefixed with 'sha256='")
    void shouldValidatePrefixedSignature() {
        String payload = "{\"eventId\":\"evt_100\",\"amount\":99.99}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        String signature = "sha256=" + validator.calculateHmac(payloadBytes);

        assertThat(validator.isValidSignature(payloadBytes, signature)).isTrue();
    }

    @Test
    @DisplayName("Should reject tampered payload with originally valid signature")
    void shouldRejectTamperedPayload() {
        String originalPayload = "{\"eventId\":\"evt_100\",\"amount\":99.99}";
        String tamperedPayload = "{\"eventId\":\"evt_100\",\"amount\":0.99}";

        String signature = validator.calculateHmac(originalPayload.getBytes(StandardCharsets.UTF_8));

        assertThat(validator.isValidSignature(tamperedPayload.getBytes(StandardCharsets.UTF_8), signature)).isFalse();
    }

    @Test
    @DisplayName("Should reject signature generated with a different secret")
    void shouldRejectSignatureFromDifferentSecret() {
        HmacSignatureValidator attackerValidator = new HmacSignatureValidator("different_attacker_secret");
        String payload = "{\"eventId\":\"evt_100\",\"amount\":99.99}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        String forgedSignature = attackerValidator.calculateHmac(payloadBytes);

        assertThat(validator.isValidSignature(payloadBytes, forgedSignature)).isFalse();
    }

    @Test
    @DisplayName("Should reject null or blank signatures")
    void shouldRejectNullOrBlankSignatures() {
        byte[] payloadBytes = "test".getBytes(StandardCharsets.UTF_8);

        assertThat(validator.isValidSignature(payloadBytes, null)).isFalse();
        assertThat(validator.isValidSignature(payloadBytes, "")).isFalse();
        assertThat(validator.isValidSignature(payloadBytes, "   ")).isFalse();
        assertThat(validator.isValidSignature(null, "some_sig")).isFalse();
    }
}
