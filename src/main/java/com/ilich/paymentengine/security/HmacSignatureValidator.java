package com.ilich.paymentengine.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class HmacSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(HmacSignatureValidator.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String webhookSecret;

    public HmacSignatureValidator(@Value("${payment.webhook.secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    /**
     * Calculates the HMAC-SHA256 hash of the payload using the shared secret.
     */
    public String calculateHmac(byte[] payloadBytes) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256
            );
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(payloadBytes);
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC-SHA256 signature", e);
            throw new IllegalStateException("Crypto algorithm error during HMAC calculation", e);
        }
    }

    /**
     * Validates the provided signature against the computed HMAC of the payload.
     * Uses MessageDigest.isEqual() to prevent timing attacks.
     * Supports signatures formatted as pure hex or 'sha256=<hex>'.
     */
    public boolean isValidSignature(byte[] payloadBytes, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank() || payloadBytes == null) {
            return false;
        }

        String normalizedProvided = providedSignature.trim().toLowerCase();
        if (normalizedProvided.startsWith("sha256=")) {
            normalizedProvided = normalizedProvided.substring("sha256=".length()).trim();
        }

        String expectedHex = calculateHmac(payloadBytes).toLowerCase();

        byte[] expectedBytes = expectedHex.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = normalizedProvided.getBytes(StandardCharsets.UTF_8);

        // Constant-time comparison
        boolean matches = MessageDigest.isEqual(expectedBytes, providedBytes);
        if (!matches) {
            log.debug("HMAC mismatch. Expected length: {}, Provided length: {}", expectedBytes.length, providedBytes.length);
        }
        return matches;
    }
}
